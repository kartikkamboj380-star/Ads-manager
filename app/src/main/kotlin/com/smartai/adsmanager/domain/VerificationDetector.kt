package com.smartai.adsmanager.domain

/**
 * Detects whether an advertisement was actually verified/completed.
 *
 * Completion MUST be proven by a real signal (an official API response or the target
 * app's legitimate reward-confirmation UI). A fixed delay is never treated as proof.
 */
interface VerificationDetector {
    fun detect(screenText: String, elapsedMs: Long): VerificationSignal
}

/**
 * Detects legitimate reward-confirmation text produced by the target app (e.g.
 * "Reward received", "Reward granted", "Reward credited"). Maps to
 * [VerificationSource.APP_LEGITIMATE_MECHANISM].
 */
class TextPatternVerificationDetector : VerificationDetector {

    private val patterns = listOf(
        Regex("reward\\s+received"),
        Regex("reward\\s+granted"),
        Regex("reward\\s+credited"),
        Regex("reward\\s+added"),
        Regex("reward\\s+verified"),
        Regex("reward\\s+claimed"),
        Regex("\\bclaimed\\s+successfully\\b"),
        Regex("\\bcompleted\\s+successfully\\b"),
    )

    override fun detect(screenText: String, elapsedMs: Long): VerificationSignal {
        val text = screenText.lowercase()
        val match = patterns.firstOrNull { it.containsMatchIn(text) }
        return if (match != null) {
            VerificationSignal(
                detected = true,
                source = VerificationSource.APP_LEGITIMATE_MECHANISM,
                message = "Reward confirmation detected on screen.",
            )
        } else {
            VerificationSignal(
                detected = false,
                source = VerificationSource.NONE,
                message = "No reward-confirmation signal detected yet.",
            )
        }
    }
}

/** A detector that never finds a signal — used when no official integration exists. */
class NoOpVerificationDetector : VerificationDetector {
    override fun detect(screenText: String, elapsedMs: Long): VerificationSignal =
        VerificationSignal(
            detected = false,
            source = VerificationSource.NONE,
            message = "No automatic verification available; use manual confirmation.",
        )
}
