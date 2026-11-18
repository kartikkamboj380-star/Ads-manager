package com.smartai.adsmanager.service

import android.graphics.Bitmap
import android.media.projection.MediaProjection
import com.smartai.adsmanager.domain.AgentEvent
import com.smartai.adsmanager.domain.AgentEventType
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
    private var framesProcessed = 0
    private var lastFramePublishedAt = 0L
    private var previousRecognized = false
    private var previousTargetAvailable = false

    // --- Live monitor outputs ----------------------------------------------------
    /** Latest captured frame, throttled for the live monitor (updated ~2 fps). */
    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrameFlow: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    /** Rolling activity log of what the agent observed/did (for the live monitor). */
    private val _events = MutableStateFlow<List<AgentEvent>>(emptyList())
    val eventsFlow: StateFlow<List<AgentEvent>> = _events.asStateFlow()

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
        framesProcessed = 0
        previousRecognized = false
        previousTargetAvailable = false
        _events.value = emptyList()
        _latestFrame.value = null
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
            agent.captureFailed("Failed to start screen capture: ${e.message ?: e.javaClass.simpleName}")
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
                        // Publish the frame to the live monitor at a throttled rate (~2 fps)
                        // so the UI can show what the agent is seeing without heavy recomposition.
                        val now = clock.now()
                        if (now - lastFramePublishedAt >= FRAME_PUBLISH_INTERVAL_MS) {
                            lastFramePublishedAt = now
                            _latestFrame.value = frame
                        }

                        if (!targetAppReported) {
                            targetAppReported = true
                            agent.targetAppDetected()
                            log(AgentEventType.DETECTION, "Target app detected; watching for its Mining screen.")
                        }
                        agent.setLastFrameTime(frameProcessor.lastFrameTimeEpochMs)
                        framesProcessed++
                        val analysis = engine.recognizeFromBitmap(frame).copy(
                            framesProcessed = framesProcessed,
                        )
                        _analysis.value = analysis

                        // Log meaningful recognition changes (throttled to avoid spam).
                        logRecognition(analysis, previousRecognized, previousTargetAvailable)
                        previousRecognized = analysis.recognized
                        previousTargetAvailable = analysis.targetButtonAvailable

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
        agent.captureFailed(message)
        _error.value = message
        _running.value = false
        log(AgentEventType.ERROR, message)
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
        framesProcessed = 0
        _error.value = null
        _latestFrame.value = null
        agent.stop()
    }

    // --- Loading flow (called by the UI) -------------------------------------

    fun userStartedLoading() {
        agent.userStartedLoading()
        log(AgentEventType.ACTION, "You started loading the ad. I'm watching for a real confirmation signal.")
    }

    fun requestRetry() {
        agent.requestRetry()
    }

    fun clearError() {
        agent.clearError()
        _error.value = null
    }

    // --- Recognition state driving -------------------------------------------

    /** Append an event to the rolling activity log (capped to the last [MAX_LOG_EVENTS]). */
    private fun log(type: AgentEventType, message: String) {
        val entry = AgentEvent(clock.now(), type, message)
        _events.value = (_events.value + entry).takeLast(MAX_LOG_EVENTS)
    }

    /** Log recognition state changes (throttled to meaningful transitions only). */
    private fun logRecognition(analysis: ScreenAnalysis, wasRecognized: Boolean, wasTarget: Boolean) {
        if (!wasRecognized && analysis.recognized) {
            log(AgentEventType.DETECTION, "Mining screen detected on the target app.")
        }
        if (!wasTarget && analysis.targetButtonAvailable) {
            val target = analysis.targetElement
            val where = target?.let { " (around ${it.left},${it.top})" } ?: ""
            log(AgentEventType.DETECTION, "Loading ad control found$where. You can tap it to load.")
        }
        if (wasTarget && !analysis.targetButtonAvailable && analysis.recognized) {
            log(AgentEventType.INFO, "Loading ad control is not currently visible; continuing to monitor.")
        }
        if (!analysis.ocrSucceeded && analysis.pipelineError != null && framesProcessed == 1) {
            log(AgentEventType.ERROR, analysis.pipelineError)
        }
    }

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

    /** How often the live monitor frame preview is refreshed (~2 fps). */
    private const val FRAME_PUBLISH_INTERVAL_MS = 500L

    /** Maximum number of activity-log entries retained for the live monitor. */
    private const val MAX_LOG_EVENTS = 60
}
