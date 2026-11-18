package com.smartai.adsmanager

import com.smartai.adsmanager.domain.TextPatternVerificationDetector
import com.smartai.adsmanager.domain.VerificationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationDetectorTest {

    private val detector = TextPatternVerificationDetector()

    @Test
    fun `detects legitimate reward confirmation text`() {
        val signal = detector.detect("Reward received", 0)
        assertTrue(signal.detected)
        assertEquals(VerificationSource.APP_LEGITIMATE_MECHANISM, signal.source)
    }

    @Test
    fun `does not treat plain loading text as confirmation`() {
        val signal = detector.detect("Loading ad please wait", 0)
        assertFalse(signal.detected)
        assertEquals(VerificationSource.NONE, signal.source)
    }

    @Test
    fun `does not treat a long delay as confirmation`() {
        // Even after a long elapsed time, no signal without confirmation text.
        val signal = detector.detect("Still loading", 120_000)
        assertFalse(signal.detected)
    }
}
