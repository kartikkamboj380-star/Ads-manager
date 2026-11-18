package com.smartai.adsmanager.domain

/**
 * AI Core — the orchestration/explanation layer.
 *
 * It understands the application state, recognizes the target Mining screen, and explains
 * what is happening. It guides the user exclusively to the Loading ad control and never
 * directs interaction with Offers, Subscribe, Wallet, Contracts, Play for Rewards or any
 * other mining plan. It performs no automatic clicking of any kind.
 */
class AiCore(
    private val matcher: MiningScreenMatcher,
) {

    /** Recognize the current screen and whether the Loading ad control is available. */
    fun analyzeScreen(screenText: String, elapsedMs: Long = 0L): ScreenAnalysis =
        matcher.analyze(screenText, elapsedMs)

    /** Human-readable guidance for the given state. Only references the Loading ad. */
    fun guidance(state: LoadingState): String = when (state) {
        LoadingState.IDLE ->
            "Assistance is idle. Start a session and open the target app's Mining screen."

        LoadingState.PERMISSION_REQUESTED ->
            "Please grant the screen-capture permission so I can watch the target screen."

        LoadingState.PERMISSION_GRANTED ->
            "Permission granted. I'm starting the consented screen capture now."

        LoadingState.CAPTURE_INITIALIZING ->
            "Initializing the screen capture. This only records what you explicitly allowed."

        LoadingState.CAPTURE_ACTIVE ->
            "Screen capture is active. Open the target app and I'll look for its Mining screen."

        LoadingState.TARGET_APP_DETECTED ->
            "I can see the selected target app. I'm looking for its Mining screen."

        LoadingState.TARGET_SCREEN_DETECTED ->
            "I've detected the target app's Mining screen. I'm looking for the Loading ad control."

        LoadingState.SCREEN_DETECTED ->
            "I've detected the target app's Mining screen. I'm looking for the Loading ad control."

        LoadingState.TARGET_BUTTON_AVAILABLE ->
            "The Loading ad control is available. I can guide you to it — you decide when to tap."

        LoadingState.USER_ACTION_REQUIRED ->
            "Please tap the Loading ad control yourself to begin loading. I will not tap it for you."

        LoadingState.LOADING ->
            "The ad is loading. I'm watching for a real reward-confirmation signal — not a timer."

        LoadingState.WAITING_FOR_VERIFICATION ->
            "A reward-confirmation signal was detected. I'm verifying the reward now."

        LoadingState.COMPLETED ->
            "The ad completed and the reward was confirmed through a legitimate signal."

        LoadingState.FAILED ->
            "The attempt failed. You can retry manually or stop the session."

        LoadingState.TIMEOUT ->
            "Loading is taking longer than expected. You can keep monitoring, retry, or stop."

        LoadingState.CAPTURE_STOPPED ->
            "Screen capture has stopped. Restart assistance to monitor again."

        LoadingState.PERMISSION_REVOKED ->
            "Screen-capture permission was revoked. Grant it again to continue monitoring."

        LoadingState.STOPPED ->
            "Monitoring has been stopped. No further action is being taken."
    }

    /** Build a full status explanation for the UI. */
    fun explain(status: AssistanceStatus): String {
        val base = guidance(status.state)
        val extra = buildString {
            if (status.timeoutWarning) {
                append(" Loading has been running for ${status.elapsedMs / 1000}s and is approaching the time limit.")
            }
            if (status.state == LoadingState.TIMEOUT) {
                append(" The time limit was exceeded. You can retry or stop.")
            }
            if (status.targetButtonAvailable && status.state != LoadingState.LOADING) {
                append(" The Loading ad control is available when you're ready.")
            }
        }
        return base + extra
    }

    /** Safe-mode summary shown on the dashboard. */
    fun safetySummary(): String =
        "This assistant only identifies and guides you to the Loading ad control. " +
            "It never auto-clicks ads, never touches Offers, Subscribe, Wallet or other mining " +
            "plans, and never manufactures impressions. You remain in control at every step."
}
