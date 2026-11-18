package com.smartai.adsmanager.service

import com.smartai.adsmanager.domain.AssistanceStatus
import com.smartai.adsmanager.domain.CaptureStatus
import com.smartai.adsmanager.domain.Clock
import com.smartai.adsmanager.domain.LoadingMonitor
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.LoadingStateMachine
import com.smartai.adsmanager.domain.TransitionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates the full agent lifecycle: the capture/permission states and the loading
 * state machine, and produces the single authoritative [AssistanceStatus] flow consumed
 * by the UI.
 *
 * It owns a [LoadingStateMachine] and a [LoadingMonitor]. Capture-pipeline state
 * (capture status, last frame time, last error, agent running flag) is maintained here
 * and merged into every emitted status so the dashboard can show "Screen Capture:
 * ACTIVE/INACTIVE", "AI Agent: RUNNING/STOPPED", last frame time, session duration and
 * the last error without the orchestrator rebuilding the status itself.
 *
 * The agent never auto-clicks; it only recognizes the screen and monitors loading.
 */
class AgentStateManager(clock: Clock) {

    val stateMachine = LoadingStateMachine()
    private val monitor = LoadingMonitor(stateMachine, clock).apply {
        setStatusListener { emit() }
    }

    private val _status = MutableStateFlow<AssistanceStatus>(initialStatus())
    val statusFlow: StateFlow<AssistanceStatus> = _status.asStateFlow()

    // Capture-pipeline state, merged into every emitted status.
    private var captureStatus: CaptureStatus = CaptureStatus.INACTIVE
    private var agentRunning: Boolean = false
    private var lastFrameTimeEpochMs: Long = 0L
    private var lastError: String? = null

    val state: LoadingState get() = stateMachine.state

    // --- Capture / permission lifecycle -------------------------------------

    fun permissionRequested() = applyTransition { stateMachine.onPermissionRequested() }

    fun permissionGranted() = applyTransition { stateMachine.onPermissionGranted() }

    fun captureInitializing() {
        applyTransition { stateMachine.onCaptureInitializing() }
        captureStatus = CaptureStatus.INITIALIZING
        emit()
    }

    /** The capture pipeline became active (VirtualDisplay created, frames flowing). */
    fun captureStarted() {
        applyTransition { stateMachine.onCaptureActive() }
        captureStatus = CaptureStatus.ACTIVE
        agentRunning = true
        emit()
    }

    fun targetAppDetected() = applyTransition { stateMachine.onTargetAppDetected() }

    fun targetScreenDetected() = applyTransition { stateMachine.onTargetScreenDetected() }

    fun captureStopped() {
        applyTransition { stateMachine.onCaptureStopped() }
        captureStatus = CaptureStatus.STOPPED
        agentRunning = false
        emit()
    }

    fun permissionRevoked() {
        applyTransition { stateMachine.onPermissionRevoked() }
        captureStatus = CaptureStatus.REVOKED
        agentRunning = false
        emit()
    }

    /** The capture pipeline failed to initialize or stopped unexpectedly. */
    fun captureFailed(message: String) {
        lastError = message
        applyTransition { stateMachine.onFailed() }
        agentRunning = false
        emit()
    }

    // --- Capture-pipeline telemetry (no state transition) --------------------

    fun setLastFrameTime(epochMs: Long) {
        lastFrameTimeEpochMs = epochMs
        emit()
    }

    fun clearError() {
        if (lastError != null) {
            lastError = null
            emit()
        }
    }

    // --- Loading flow (delegates to the monitor) ----------------------------

    fun userStartedLoading() {
        monitor.onLoadingStarted()
    }

    fun tick(screenText: String) {
        monitor.tick(screenText)
    }

    fun requestRetry() {
        monitor.requestRetry()
    }

    /** Stop everything: monitor → stopped, capture → stopped, agent not running. */
    fun stop() {
        monitor.stop()
        captureStatus = CaptureStatus.STOPPED
        agentRunning = false
        emit()
    }

    fun reset() {
        stateMachine.reset()
        captureStatus = CaptureStatus.INACTIVE
        agentRunning = false
        lastFrameTimeEpochMs = 0L
        lastError = null
        emit()
    }

    private fun applyTransition(block: () -> TransitionOutcome): TransitionOutcome {
        val outcome = block()
        emit()
        return outcome
    }

    private fun emit() {
        val base = monitor.status()
        _status.value = base.copy(
            captureActive = captureStatus == CaptureStatus.ACTIVE,
            agentRunning = agentRunning,
            lastFrameTimeEpochMs = lastFrameTimeEpochMs,
            captureStatus = captureStatus,
            lastError = lastError,
        )
    }

    private fun initialStatus(): AssistanceStatus = monitor.status().copy(
        captureActive = false,
        agentRunning = false,
        captureStatus = CaptureStatus.INACTIVE,
    )
}
