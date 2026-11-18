package com.smartai.adsmanager.domain

/**
 * Recognizes the target app's Mining screen from OCR text and identifies the exact
 * "Loading ad" control.
 *
 * Only [TargetScreenOption.LOADING_AD] is ever treated as a target. The matcher never
 * returns any other option as actionable — it only reports what is present so the AI
 * core can guide the user exclusively to the Loading ad control.
 *
 * Pure logic (no Android dependencies) so it is fully unit-testable with mock OCR text.
 */
class MiningScreenMatcher {

    /**
     * Keyword/regex rules for each Mining-screen option. Matching is case-insensitive
     * and uses word boundaries for short tokens to reduce false positives.
     */
    private val optionPatterns: Map<TargetScreenOption, List<Regex>> = mapOf(
        TargetScreenOption.ACTIVE to listOf(
            Regex("\\bactive\\b"),
        ),
        TargetScreenOption.PLAY_FOR_REWARDS to listOf(
            Regex("play\\s+for\\s+rewards?"),
            Regex("play\\s+(?:to|&)\\s*earn"),
        ),
        TargetScreenOption.LOADING_AD to listOf(
            Regex("loading\\s+ads?"),
            Regex("load\\s+ads?"),
        ),
        TargetScreenOption.SHOW_OFFERS to listOf(
            Regex("show\\s+offers?"),
            Regex("offer\\s+wall"),
            Regex("\\boffers?\\b"),
        ),
        TargetScreenOption.SUBSCRIBE to listOf(
            Regex("\\bsubscribe\\b"),
            Regex("\\bsubscription\\b"),
            Regex("\\bpremium\\b"),
        ),
    )

    /** Phrases that indicate a visible loading indicator (not the "Loading ad" label). */
    private val loadingIndicatorPatterns = listOf(
        Regex("please\\s+wait"),
        Regex("processing"),
        Regex("verifying"),
        Regex("checking"),
        Regex("loading\\s*…"),
        Regex("loading\\.{2,}"),
    )

    /** Normalize OCR text: lowercase and collapse whitespace. */
    private fun normalize(raw: String): String =
        raw.lowercase().replace(Regex("\\s+"), " ").trim()

    fun detectOptions(rawText: String): Set<TargetScreenOption> {
        val text = normalize(rawText)
        if (text.isEmpty()) return emptySet()
        val found = mutableSetOf<TargetScreenOption>()
        optionPatterns.forEach { (option, patterns) ->
            if (patterns.any { it.containsMatchIn(text) }) found.add(option)
        }
        return found
    }

    fun isLoadingIndicatorVisible(rawText: String): Boolean {
        val text = normalize(rawText)
        return loadingIndicatorPatterns.any { it.containsMatchIn(text) }
    }

    /** True only when the exact "Loading ad" control is present. */
    fun isTargetAvailable(rawText: String): Boolean =
        TargetScreenOption.LOADING_AD in detectOptions(rawText)

    /**
     * Full analysis used by the AI core: is the Mining screen recognized, is the
     * Loading ad control available, is a loading indicator visible, and any error hint.
     */
    fun analyze(rawText: String, loadingElapsedMs: Long = 0L): ScreenAnalysis {
        val options = detectOptions(rawText)
        val recognized = options.isNotEmpty()
        val targetAvailable = TargetScreenOption.LOADING_AD in options
        val loadingVisible = isLoadingIndicatorVisible(rawText)
        val error: String? = when {
            recognized && !targetAvailable ->
                "Mining screen detected, but the Loading ad control is not currently available."
            !recognized ->
                "Target Mining screen not recognized in the current view."
            else -> null
        }
        return ScreenAnalysis(
            recognized = recognized,
            matchedScreen = if (recognized) MINING_SCREEN_NAME else null,
            detectedOptions = options,
            targetButtonAvailable = targetAvailable,
            loadingIndicatorVisible = loadingVisible,
            loadingElapsedMs = loadingElapsedMs,
            error = error,
            confidence = computeConfidence(rawText, recognized, targetAvailable),
        )
    }

    /**
     * Confidence in [0.0, 1.0] for the recognition result.
     *
     * Never a decision driver on its own: low confidence means the agent must ask the
     * user rather than act. Recognizing the exact Loading ad control is the strongest
     * signal; a modest bonus is added for a richer (longer) OCR sample.
     */
    fun computeConfidence(rawText: String, recognized: Boolean, targetAvailable: Boolean): Double {
        if (!recognized) return 0.0
        val textLength = normalize(rawText).length.coerceAtLeast(0)
        val lengthScore = (textLength / 200.0).coerceIn(0.0, 0.05)
        val base = if (targetAvailable) 0.90 else 0.60
        return (base + lengthScore).coerceIn(0.0, 1.0)
    }

    companion object {
        const val MINING_SCREEN_NAME = "Mining"
    }
}
