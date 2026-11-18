package com.smartai.adsmanager.domain

/**
 * Drives the loading phase of the state machine and monitors elapsed time.
 *
 * - It only enters [LoadingState.LOADING] when the *user* has manually started the ad
 *   (via [onLoadingStarted]); it never starts loading by itself.
 * - It proves completion only through a [VerificationDetector] signal, never a delay.
 * - It detects long loading times, shows a timeout warning at a threshold, and fires a
 *   [LoadingState.TIMEOUT] transition once the configured timeout is exceeded.
 */
class LoadingMonitor(
    private val stateMachine: LoadingStateMachine,
    private val clock: Clock,
    private val timeoutMs: Long = DEFAULT_LOADING_TIMEOUT_MS,
    private val warningThresholdMs: Long = DEFAULT_WARNING_THRESHOLD_MS,
    private val detector: VerificationDetector = TextPatternVerificationDetector(),
) {
    private var loadingStartMs: Long? = null
    private var statusListener: ((AssistanceStatus) -> Unit)? = null

    fun setStatusListener(listener: (AssistanceStatus) -> Unit) {
        statusListener = listener
    }

    /** Called when the user manually taps the Loading ad control. */
    fun onLoadingStarted() {
        // Ensure we are at USER_ACTION_REQUIRED (handles both TARGET_BUTTON_AVAILABLE and
        // USER_ACTION_REQUIRED entry points), then enter LOADING.
        stateMachine.onUserActionRequired()
        stateMachine.onUserActionStarted()
        loadingStartMs = clock.now()
        emit()
    }

    fun elapsedMs(): Long =
        loadingStartMs?.let { (clock.now() - it).coerceAtLeast(0L) } ?: 0L

    fun isLoading(): Boolean = stateMachine.state == LoadingState.LOADING

    /** Whether the elapsed time has crossed the soft warning threshold. */
    fun isWarningActive(): Boolean =
        isLoading() && elapsedMs() >= warningThresholdMs && elapsedMs() < timeoutMs

    /**
     * Advance the monitor. Checks for a verification signal (proving completion) and,
     * if still loading, whether the timeout has been exceeded.
     */
    fun tick(screenText: String = "") {
        when (stateMachine.state) {
            LoadingState.LOADING -> {
                val elapsed = elapsedMs()
                // 1) Completion is only claimed when a real signal is detected.
                val signal = detector.detect(screenText, elapsed)
                if (signal.detected) {
                    stateMachine.onWaitingForVerification()
                    stateMachine.onCompleted()
                    emit()
                    return
                }
                // 2) Timeout handling — continue monitoring, then warn/timeout.
                if (elapsed >= timeoutMs) {
                    stateMachine.onTimeout()
                    emit()
                    return
                }
            }
            else -> {
                // No-op for non-loading states.
            }
        }
        emit()
    }

    /** Stop monitoring and return the machine to a terminal STOPPED state. */
    fun stop() {
        stateMachine.onStopped()
        loadingStartMs = null
        emit()
    }

    /** Manual retry from TIMEOUT/FAILED: re-enter USER_ACTION_REQUIRED so the user can act again. */
    fun requestRetry() {
        stateMachine.onUserActionRequired()
        loadingStartMs = null
        emit()
    }

    fun status(message: String = currentMessage()): AssistanceStatus {
        return AssistanceStatus(
            state = stateMachine.state,
            message = message,
            elapsedMs = elapsedMs(),
            targetButtonAvailable = stateMachine.state == LoadingState.TARGET_BUTTON_AVAILABLE ||
                stateMachine.state == LoadingState.USER_ACTION_REQUIRED,
            loadingIndicatorVisible = isLoading(),
            timeoutWarning = isWarningActive(),
        )
    }

    private fun emit() {
        statusListener?.invoke(status())
    }

    private fun currentMessage(): String = when (stateMachine.state) {
        LoadingState.IDLE -> "Idle. Start ad assistance to begin."
        LoadingState.PERMISSION_REQUESTED -> "Waiting for screen-capture permission."
        LoadingState.PERMISSION_GRANTED -> "Screen-capture permission granted. Starting capture."
        LoadingState.CAPTURE_INITIALIZING -> "Initializing screen capture."
        LoadingState.CAPTURE_ACTIVE -> "Screen capture is active. Looking for the target app."
        LoadingState.TARGET_APP_DETECTED -> "The selected target app is detected."
        LoadingState.TARGET_SCREEN_DETECTED -> "Target Mining screen detected."
        LoadingState.SCREEN_DETECTED -> "Target Mining screen detected."
        LoadingState.TARGET_BUTTON_AVAILABLE ->
            "The Loading ad control is available on the Mining screen."
        LoadingState.USER_ACTION_REQUIRED ->
            "Please tap the Loading ad control yourself to start loading."
        LoadingState.LOADING -> "Loading in progress. Waiting for a reward-confirmation signal."
        LoadingState.WAITING_FOR_VERIFICATION -> "Confirmation signal received. Verifying reward."
        LoadingState.COMPLETED -> "Ad completed and reward confirmed."
        LoadingState.FAILED -> "The loading attempt failed. You can retry or stop."
        LoadingState.TIMEOUT ->
            "Loading exceeded the expected time. You can retry or stop monitoring."
        LoadingState.CAPTURE_STOPPED -> "Screen capture stopped."
        LoadingState.PERMISSION_REVOKED ->
            "Screen-capture permission was revoked. Request new consent to continue."
        LoadingState.STOPPED -> "Monitoring stopped."
    }

    companion object {
        const val DEFAULT_LOADING_TIMEOUT_MS = 90_000L
        const val DEFAULT_WARNING_THRESHOLD_MS = 60_000L
    }
}
