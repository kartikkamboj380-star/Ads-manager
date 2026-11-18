package com.smartai.adsmanager.domain

/** Result of a state-machine transition attempt. */
enum class TransitionResult {
    /** The transition was valid and applied. */
    SUCCESS,
    /** The transition was a no-op because the machine was already in that state. */
    DUPLICATE,
    /** The transition is not allowed from the current state. */
    ILLEGAL,
}

data class TransitionOutcome(
    val result: TransitionResult,
    val from: LoadingState,
    val to: LoadingState,
)

/**
 * The guarded ad-loading state machine.
 *
 * Transitions are validated against an explicit allow-list so the UI can never enter
 * an inconsistent state. Duplicate transitions are reported (not thrown) and illegal
 * transitions are rejected — the machine keeps its current state in that case.
 *
 * The only permitted target on the target app's Mining screen is [LoadingState.LOADING]
 * via [onUserActionStarted]; the machine never models any interaction with Offers,
 * Subscribe, Wallet, Contracts or other mining plans.
 */
class LoadingStateMachine(initial: LoadingState = LoadingState.IDLE) {

    var state: LoadingState = initial
        private set

    private val listeners = mutableListOf<(LoadingState) -> Unit>()

    /**
     * Allowed forward transitions. Loops back to IDLE/STOPPED are allowed from terminal
     * states so the user can retry or start over.
     */
    private val allowed: Map<LoadingState, Set<LoadingState>> = mapOf(
        LoadingState.IDLE to setOf(
            LoadingState.PERMISSION_REQUESTED,
            LoadingState.SCREEN_DETECTED,
            LoadingState.STOPPED,
        ),
        LoadingState.PERMISSION_REQUESTED to setOf(
            LoadingState.PERMISSION_GRANTED,
            LoadingState.PERMISSION_REVOKED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
        ),
        LoadingState.PERMISSION_GRANTED to setOf(
            LoadingState.CAPTURE_INITIALIZING,
            LoadingState.PERMISSION_REVOKED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
        ),
        LoadingState.CAPTURE_INITIALIZING to setOf(
            LoadingState.CAPTURE_ACTIVE,
            LoadingState.FAILED,
            LoadingState.PERMISSION_REVOKED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
        ),
        LoadingState.CAPTURE_ACTIVE to setOf(
            LoadingState.TARGET_APP_DETECTED,
            LoadingState.FAILED,
            LoadingState.PERMISSION_REVOKED,
            LoadingState.CAPTURE_STOPPED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
        ),
        LoadingState.TARGET_APP_DETECTED to setOf(
            LoadingState.TARGET_SCREEN_DETECTED,
            LoadingState.SCREEN_DETECTED,
            LoadingState.FAILED,
            LoadingState.CAPTURE_STOPPED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
        ),
        LoadingState.TARGET_SCREEN_DETECTED to setOf(
            LoadingState.TARGET_BUTTON_AVAILABLE,
            LoadingState.SCREEN_DETECTED,
            LoadingState.FAILED,
            LoadingState.CAPTURE_STOPPED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
        ),
        LoadingState.SCREEN_DETECTED to setOf(
            LoadingState.TARGET_BUTTON_AVAILABLE,
            LoadingState.FAILED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
            LoadingState.CAPTURE_STOPPED,
        ),
        LoadingState.TARGET_BUTTON_AVAILABLE to setOf(
            LoadingState.USER_ACTION_REQUIRED,
            LoadingState.FAILED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
            LoadingState.CAPTURE_STOPPED,
        ),
        LoadingState.USER_ACTION_REQUIRED to setOf(
            LoadingState.LOADING,
            LoadingState.TARGET_BUTTON_AVAILABLE,
            LoadingState.FAILED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
            LoadingState.CAPTURE_STOPPED,
        ),
        LoadingState.LOADING to setOf(
            LoadingState.WAITING_FOR_VERIFICATION,
            LoadingState.TIMEOUT,
            LoadingState.FAILED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
            LoadingState.CAPTURE_STOPPED,
        ),
        LoadingState.WAITING_FOR_VERIFICATION to setOf(
            LoadingState.COMPLETED,
            LoadingState.FAILED,
            LoadingState.STOPPED,
            LoadingState.IDLE,
            LoadingState.CAPTURE_STOPPED,
        ),
        LoadingState.COMPLETED to setOf(
            LoadingState.IDLE,
            LoadingState.STOPPED,
            LoadingState.CAPTURE_STOPPED,
            LoadingState.PERMISSION_REQUESTED,
        ),
        LoadingState.FAILED to setOf(
            LoadingState.USER_ACTION_REQUIRED,
            LoadingState.IDLE,
            LoadingState.STOPPED,
            LoadingState.CAPTURE_STOPPED,
            LoadingState.PERMISSION_REVOKED,
            LoadingState.PERMISSION_REQUESTED,
        ),
        LoadingState.TIMEOUT to setOf(
            LoadingState.USER_ACTION_REQUIRED,
            LoadingState.IDLE,
            LoadingState.STOPPED,
            LoadingState.CAPTURE_STOPPED,
            LoadingState.PERMISSION_REQUESTED,
        ),
        LoadingState.CAPTURE_STOPPED to setOf(
            LoadingState.IDLE,
            LoadingState.STOPPED,
            LoadingState.PERMISSION_REQUESTED,
        ),
        LoadingState.PERMISSION_REVOKED to setOf(
            LoadingState.IDLE,
            LoadingState.STOPPED,
            LoadingState.PERMISSION_REQUESTED,
        ),
        LoadingState.STOPPED to setOf(
            LoadingState.IDLE,
            LoadingState.PERMISSION_REQUESTED,
        ),
    )

    fun addListener(listener: (LoadingState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LoadingState) -> Unit) {
        listeners.remove(listener)
    }

    fun transition(target: LoadingState): TransitionOutcome {
        val from = state
        if (from == target) {
            return TransitionOutcome(TransitionResult.DUPLICATE, from, target)
        }
        val permitted = allowed[from]
        if (permitted == null || target !in permitted) {
            return TransitionOutcome(TransitionResult.ILLEGAL, from, target)
        }
        state = target
        listeners.forEach { it(target) }
        return TransitionOutcome(TransitionResult.SUCCESS, from, target)
    }

    fun reset() {
        transition(LoadingState.IDLE)
    }

    // --- Convenience intent methods -------------------------------------------------

    fun onScreenDetected(): TransitionOutcome =
        transition(LoadingState.SCREEN_DETECTED)

    fun onTargetButtonAvailable(): TransitionOutcome =
        transition(LoadingState.TARGET_BUTTON_AVAILABLE)

    fun onUserActionRequired(): TransitionOutcome =
        transition(LoadingState.USER_ACTION_REQUIRED)

    /**
     * Called only when the *user* manually starts loading the ad. The assistant never
     * performs this action automatically.
     */
    fun onUserActionStarted(): TransitionOutcome =
        transition(LoadingState.LOADING)

    // --- Capture / permission lifecycle intent methods -----------------------

    fun onPermissionRequested(): TransitionOutcome =
        transition(LoadingState.PERMISSION_REQUESTED)

    fun onPermissionGranted(): TransitionOutcome =
        transition(LoadingState.PERMISSION_GRANTED)

    fun onCaptureInitializing(): TransitionOutcome =
        transition(LoadingState.CAPTURE_INITIALIZING)

    fun onCaptureActive(): TransitionOutcome =
        transition(LoadingState.CAPTURE_ACTIVE)

    fun onTargetAppDetected(): TransitionOutcome =
        transition(LoadingState.TARGET_APP_DETECTED)

    fun onTargetScreenDetected(): TransitionOutcome =
        transition(LoadingState.TARGET_SCREEN_DETECTED)

    fun onCaptureStopped(): TransitionOutcome =
        transition(LoadingState.CAPTURE_STOPPED)

    fun onPermissionRevoked(): TransitionOutcome =
        transition(LoadingState.PERMISSION_REVOKED)

    fun onWaitingForVerification(): TransitionOutcome =
        transition(LoadingState.WAITING_FOR_VERIFICATION)

    fun onCompleted(): TransitionOutcome =
        transition(LoadingState.COMPLETED)

    fun onFailed(): TransitionOutcome =
        transition(LoadingState.FAILED)

    fun onTimeout(): TransitionOutcome =
        transition(LoadingState.TIMEOUT)

    fun onStopped(): TransitionOutcome =
        transition(LoadingState.STOPPED)
}
