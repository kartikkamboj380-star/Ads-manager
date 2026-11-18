package com.smartai.adsmanager.domain

/**
 * Error Recovery Manager.
 *
 * Classifies errors and produces a recovery plan (retry / stop / monitor) while
 * avoiding duplicate or unsafe automatic actions. Retry is only suggested when it is
 * actually safe; verification failures are never auto-retried.
 */
class ErrorRecoveryManager(
    private val maxRetries: Int = 2,
) {

    fun classify(error: Throwable, state: LoadingState): ErrorCategory {
        if (state == LoadingState.TIMEOUT) return ErrorCategory.TIMEOUT
        val msg = error.message?.lowercase().orEmpty()
        return when {
            "timeout" in msg || "timed out" in msg -> ErrorCategory.TIMEOUT
            "verification" in msg || "confirm" in msg -> ErrorCategory.VERIFICATION_FAILED
            "screen" in msg || "recognize" in msg || "ocr" in msg -> ErrorCategory.SCREEN_NOT_FOUND
            "target" in msg || "button" in msg -> ErrorCategory.TARGET_MISSING
            "loading" in msg -> ErrorCategory.LOADING_ERROR
            else -> ErrorCategory.UNKNOWN
        }
    }

    fun plan(error: Throwable, state: LoadingState, retryCount: Int): RecoveryPlan =
        when (classify(error, state)) {
            ErrorCategory.TIMEOUT ->
                if (retryCount < maxRetries) {
                    RecoveryPlan(RecoveryAction.RETRY, "Loading timed out. You can retry or stop.")
                } else {
                    RecoveryPlan(RecoveryAction.STOP, "Loading timed out repeatedly. Stop and try again later.")
                }

            ErrorCategory.LOADING_ERROR ->
                if (retryCount < maxRetries) {
                    RecoveryPlan(RecoveryAction.RETRY, "A loading error occurred. You can retry or stop.")
                } else {
                    RecoveryPlan(RecoveryAction.STOP, "Loading errors persisted. Stop monitoring.")
                }

            ErrorCategory.SCREEN_NOT_FOUND ->
                RecoveryPlan(RecoveryAction.MONITOR, "Target screen not found yet. Continue monitoring.")

            ErrorCategory.TARGET_MISSING ->
                RecoveryPlan(RecoveryAction.MONITOR, "Loading ad control not available yet. Continue monitoring.")

            ErrorCategory.VERIFICATION_FAILED ->
                RecoveryPlan(
                    RecoveryAction.STOP,
                    "Reward could not be verified. Stop and confirm manually if available.",
                )

            ErrorCategory.UNKNOWN ->
                RecoveryPlan(RecoveryAction.MONITOR, "Unexpected error. Continue monitoring and review status.")
        }
}
