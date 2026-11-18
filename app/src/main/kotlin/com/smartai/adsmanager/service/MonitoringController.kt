package com.smartai.adsmanager.service

import android.media.projection.MediaProjection
import com.smartai.adsmanager.domain.AssistanceStatus
import com.smartai.adsmanager.domain.Clock
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.ScreenAnalysis
import com.smartai.adsmanager.domain.SystemClock
import com.smartai.adsmanager.screenrecognition.MlKitScreenRecognitionEngine
import com.smartai.adsmanager.screenrecognition.ScreenRecognitionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton orchestrator for an active, consented monitoring session.
 *
 * It owns an [AgentStateManager] (state machine + loading monitor + status flow),
 * captures frames via a [MediaProjectionController] and processes them with a
 * [ScreenFrameProcessor] on a background dispatcher, runs on-device OCR, and drives the
 * state machine. It exposes observable [statusFlow], [analysisFlow], [runningFlow] and
 * [errorFlow] for the UI.
 *
 * The assistant never clicks anything: it only recognizes the screen, identifies the
 * Loading ad control, and waits for the user to start loading. Completion is proven only
 * by a legitimate verification signal — never by a missing frame or a timer.
 *
 * Every operation is exception-safe: a capture or OCR failure is reported, never thrown
 * into the host process (which would crash the app).
 */
object MonitoringController {

    private val engine: ScreenRecognitionEngine = MlKitScreenRecognitionEngine()
    private val clock: Clock = SystemClock()
    private val agent = AgentStateManager(clock)

    val statusFlow: StateFlow<AssistanceStatus> = agent.statusFlow

    private val _analysis = MutableStateFlow<ScreenAnalysis>(
        ScreenAnalysis(false, null, emptySet(), false, false, 0L, "Monitoring is idle.")
    )
    val analysisFlow: StateFlow<ScreenAnalysis> = _analysis.asStateFlow()

    private val _running = MutableStateFlow(false)
    val runningFlow: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _error.asStateFlow()

    private var capture: MediaProjectionController? = null
    private var processor: ScreenFrameProcessor? = null
    private var targetAppReported = false

    val state: LoadingState get() = agent.state
    val isRunning: Boolean get() = _running.value

    // --- Capture lifecycle (called by the foreground service / ViewModel) ------

    /** User tapped Start — enter the PERMISSION_REQUESTED state. */
    fun onPermissionRequested() = agent.permissionRequested()

    /** User denied/cancelled consent — enter PERMISSION_REVOKED. */
    fun onPermissionRevoked() = agent.permissionRevoked()

    /**
     * Begin a monitoring session with an already-granted [MediaProjection]. The caller
     * (service) must have obtained explicit user consent before calling this.
     *
     * Drives the state: PERMISSION_GRANTED → CAPTURE_INITIALIZING → CAPTURE_ACTIVE, and
     * owns the capture pipeline + background frame loop.
     */
    fun startCapture(projection: MediaProjection, width: Int, height: Int, densityDpi: Int) {
        // Always start from a clean slate (handles a replaced/previous session).
        stopInternal()
        agent.reset()
        targetAppReported = false
        _analysis.value = ScreenAnalysis(false, null, emptySet(), false, false, 0L, "Starting capture.")

        agent.permissionGranted()
        agent.captureInitializing()

        val controller = MediaProjectionController(projection, width, height, densityDpi)
        controller.onProjectionStopped = {
            // The system ended the capture session (revoked / taken over). Stop gracefully
            // instead of continuing with a dead projection.
            if (_running.value) {
                _error.value = "Screen capture was stopped by the system. Request new consent to continue."
                agent.captureStopped()
                stopInternal()
            }
        }

        try {
            controller.start()
        } catch (e: Exception) {
            // Creating the VirtualDisplay can throw IllegalStateException. Report and stop;
            // never let it crash the process.
            agent.onCaptureFailed("Failed to start screen capture: ${e.message ?: e.javaClass.simpleName}")
            _error.value = agent.statusFlow.value.lastError
            stopInternal()
            return
        }

        capture = controller

        // Start the frame-processing loop on a dedicated background dispatcher.
        val frameProcessor = ScreenFrameProcessor(clock)
        processor = frameProcessor
        _running.value = true

        frameProcessor.start(
            controller = controller,
            intervalMs = FRAME_INTERVAL_MS,
            onFrame = { frame ->
                try {
                    if (!targetAppReported) {
                        targetAppReported = true
                        agent.targetAppDetected()
                    }
                    agent.setLastFrameTime(frameProcessor.lastFrameTimeEpochMs)
                    val analysis = engine.recognizeFromBitmap(frame)
                    _analysis.value = analysis
                    drive(analysis)
                    if (agent.state == LoadingState.LOADING) {
                        agent.tick(analysis.recognizedText)
                    }
                } catch (_: Exception) {
                    // A single bad frame must never kill the loop or the process.
                }
            },
        )

        // Capture pipeline is up and producing frames.
        agent.captureStarted()
        _error.value = null
    }

    /** The capture pipeline failed to initialize (called by the service on a throw). */
    fun onCaptureFailed(message: String) {
        agent.onCaptureFailed(message)
        _error.value = message
        _running.value = false
    }

    /** Handle a display size/density change (rotation) by resizing the VirtualDisplay. */
    fun resizeCapture(width: Int, height: Int, densityDpi: Int) {
        capture?.resize(width, height, densityDpi)
    }

    /** Stop monitoring, release the capture, and clear the foreground state. */
    fun stop() {
        agent.captureStopped()
        stopInternal()
    }

    private fun stopInternal() {
        _running.value = false
        processor?.release()
        processor = null
        capture?.stop()
        capture = null
        targetAppReported = false
        _error.value = null
        agent.stop()
    }

    // --- Loading flow (called by the UI) -------------------------------------

    fun userStartedLoading() {
        agent.userStartedLoading()
    }

    fun requestRetry() {
        agent.requestRetry()
    }

    fun clearError() {
        agent.clearError()
        _error.value = null
    }

    // --- Recognition state driving -------------------------------------------

    private fun drive(analysis: ScreenAnalysis) {
        when (agent.state) {
            LoadingState.IDLE ->
                if (analysis.recognized) agent.targetScreenDetected()

            LoadingState.CAPTURE_ACTIVE, LoadingState.TARGET_APP_DETECTED ->
                if (analysis.recognized) agent.targetScreenDetected()

            LoadingState.TARGET_SCREEN_DETECTED, LoadingState.SCREEN_DETECTED ->
                if (analysis.targetButtonAvailable) {
                    agent.stateMachine.onTargetButtonAvailable()
                    agent.stateMachine.onUserActionRequired()
                }

            LoadingState.TARGET_BUTTON_AVAILABLE ->
                if (!analysis.targetButtonAvailable) agent.stateMachine.onUserActionRequired()

            // USER_ACTION_REQUIRED / LOADING / terminal -> handled by user action / monitor.tick
            else -> { /* no-op */ }
        }
    }

    /** Capture/OCR polling cadence. Polling only — never a completion proof. */
    private const val FRAME_INTERVAL_MS = 1200L
}
