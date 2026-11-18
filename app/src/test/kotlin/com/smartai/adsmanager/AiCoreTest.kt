package com.smartai.adsmanager

import com.smartai.adsmanager.domain.AiCore
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.MiningScreenMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoreTest {

    private val aiCore = AiCore(MiningScreenMatcher())

    @Test
    fun `guidance never directs interaction with forbidden options`() {
        val forbidden = listOf(
            "offer", "subscribe", "wallet", "contract", "play for rewards",
            "install", "skip", "click",
        )
        for (state in LoadingState.entries) {
            val guidance = aiCore.guidance(state)
            val lower = guidance.lowercase()
            for (word in forbidden) {
                assertFalse(
                    "Guidance for $state should not mention '$word': $guidance",
                    lower.contains(word),
                )
            }
        }
    }

    @Test
    fun `guidance for user action required tells the user to tap manually`() {
        val guidance = aiCore.guidance(LoadingState.USER_ACTION_REQUIRED)
        assertTrue(guidance.contains("tap the Loading ad"))
        assertTrue(guidance.contains("yourself"))
    }

    @Test
    fun `analyzeScreen recognizes mining screen and Loading ad`() {
        val analysis = aiCore.analyzeScreen("Active  Loading ad  Show Offers  Subscribe")
        assertTrue(analysis.recognized)
        assertTrue(analysis.targetButtonAvailable)
        assertEquals("Mining", analysis.matchedScreen)
    }

    @Test
    fun `safety summary explains the restricted behavior`() {
        val summary = aiCore.safetySummary()
        assertTrue(summary.contains("never auto-clicks"))
        assertTrue(summary.contains("Loading ad"))
    }

    @Test
    fun `explain includes timeout warning detail when active`() {
        val status = com.smartai.adsmanager.domain.AssistanceStatus(
            state = LoadingState.LOADING,
            message = "m",
            elapsedMs = 70_000,
            targetButtonAvailable = false,
            loadingIndicatorVisible = true,
            timeoutWarning = true,
        )
        val explanation = aiCore.explain(status)
        assertTrue(explanation.contains("approaching the time limit"))
    }
}
