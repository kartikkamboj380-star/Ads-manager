package com.smartai.adsmanager

import com.smartai.adsmanager.domain.MiningScreenMatcher
import com.smartai.adsmanager.domain.TargetScreenOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiningScreenMatcherTest {

    private val matcher = MiningScreenMatcher()

    @Test
    fun `recognizes mining screen with all options`() {
        val text = "Active  Play for Rewards  Loading ad  Show Offers  Subscribe"
        val analysis = matcher.analyze(text)
        assertTrue(analysis.recognized)
        assertEquals("Mining", analysis.matchedScreen)
        assertEquals(setOf(
            TargetScreenOption.ACTIVE,
            TargetScreenOption.PLAY_FOR_REWARDS,
            TargetScreenOption.LOADING_AD,
            TargetScreenOption.SHOW_OFFERS,
            TargetScreenOption.SUBSCRIBE,
        ), analysis.detectedOptions)
    }

    @Test
    fun `identifies the exact Loading ad control`() {
        assertTrue(matcher.isTargetAvailable("Loading ad"))
        assertTrue(matcher.isTargetAvailable("load ads"))
        assertFalse(matcher.isTargetAvailable("Show Offers"))
    }

    @Test
    fun `target not available when Loading ad absent`() {
        val analysis = matcher.analyze("Active  Play for Rewards  Show Offers  Subscribe")
        assertTrue(analysis.recognized)
        assertFalse(analysis.targetButtonAvailable)
        assertTrue(analysis.error!!.contains("not currently available"))
    }

    @Test
    fun `unrecognized screen reports not recognized`() {
        val analysis = matcher.analyze("some random screen content")
        assertFalse(analysis.recognized)
        assertFalse(analysis.targetButtonAvailable)
    }

    @Test
    fun `loading indicator is distinguished from the Loading ad label`() {
        // "Loading ad" is a button label, not a loading indicator.
        assertFalse(matcher.isLoadingIndicatorVisible("Loading ad"))
        // Genuine loading phrases are detected.
        assertTrue(matcher.isLoadingIndicatorVisible("Please wait..."))
        assertTrue(matcher.isLoadingIndicatorVisible("Verifying reward"))
    }

    @Test
    fun `case insensitive matching`() {
        assertTrue(matcher.isTargetAvailable("LOADING AD"))
        assertTrue(matcher.isTargetAvailable("loading ad"))
    }

    @Test
    fun `confidence is high for the Loading ad control and zero when unrecognized`() {
        val withTarget = matcher.analyze("Active  Play for Rewards  Loading ad  Show Offers  Subscribe")
        assertTrue(withTarget.recognized)
        assertTrue(withTarget.targetButtonAvailable)
        assertTrue(withTarget.confidence > 0.9)

        val withoutTarget = matcher.analyze("Active  Show Offers  Subscribe")
        assertTrue(withoutTarget.recognized)
        assertFalse(withoutTarget.targetButtonAvailable)
        assertTrue(withoutTarget.confidence > 0.0)
        assertTrue(withoutTarget.confidence < withTarget.confidence)

        val unrecognized = matcher.analyze("some random screen")
        assertFalse(unrecognized.recognized)
        assertEquals(0.0, unrecognized.confidence, 1e-9)
    }

    @Test
    fun `recognizes mining screen from general keywords even without exact labels`() {
        // OCR may only partially read the screen; mining keywords still recognize it.
        val analysis = matcher.analyze("Bitcoin mining — hashrate and rewards")
        assertTrue(analysis.recognized)
        assertEquals("Mining", analysis.matchedScreen)
        assertFalse(analysis.targetButtonAvailable)
    }

    @Test
    fun `watch ad and play ad variants are treated as the Loading ad target`() {
        assertTrue(matcher.isTargetAvailable("Watch Ad"))
        assertTrue(matcher.isTargetAvailable("Play ad"))
        assertTrue(matcher.isTargetAvailable("Loading advertisement"))
    }

    @Test
    fun `ocr noise is tolerated (punctuation and extra spaces)`() {
        assertTrue(matcher.isTargetAvailable("Loading, ad!"))
        assertTrue(matcher.isTargetAvailable("load   ad"))
        assertTrue(matcher.isTargetAvailable("LOADING-AD"))
    }

    @Test
    fun `loading indicator phrases are not confused with the Loading ad label`() {
        // "Loading now..." is an indicator, not the ad-loading control.
        assertTrue(matcher.isLoadingIndicatorVisible("Loading now..."))
        assertFalse(matcher.isTargetAvailable("Loading now..."))
    }

    @Test
    fun `analysis carries pipeline diagnostics defaults`() {
        val analysis = matcher.analyze("Loading ad")
        assertEquals(0, analysis.framesProcessed)
        assertFalse(analysis.ocrSucceeded)
        assertEquals(null, analysis.pipelineError)
    }
}
