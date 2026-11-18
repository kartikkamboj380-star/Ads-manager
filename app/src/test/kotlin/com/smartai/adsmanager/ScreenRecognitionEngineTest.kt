package com.smartai.adsmanager

import com.smartai.adsmanager.domain.TargetScreenOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecognitionEngineTest {

    @Test
    fun `recognizeFromText identifies the Loading ad control`() = runBlocking {
        val engine = FakeScreenRecognitionEngine("Loading ad  Show Offers")
        val analysis = engine.recognizeFromText("Loading ad  Show Offers")
        assertTrue(analysis.recognized)
        assertTrue(analysis.targetButtonAvailable)
        assertEquals(
            setOf(TargetScreenOption.LOADING_AD, TargetScreenOption.SHOW_OFFERS),
            analysis.detectedOptions,
        )
    }

    @Test
    fun `recognizeFromBitmap delegates to the matcher via mock OCR text`() = runBlocking {
        val engine = FakeScreenRecognitionEngine("Play for Rewards  Subscribe")
        // The fake ignores the bitmap and uses its configured mock OCR text. A null frame
        // is a valid input and avoids mocking Android framework types.
        val analysis = engine.recognizeFromBitmap(null)
        assertTrue(analysis.recognized)
        assertTrue(analysis.targetButtonAvailable.not())
    }

    @Test
    fun `empty text yields unrecognized analysis`() = runBlocking {
        val engine = FakeScreenRecognitionEngine("")
        val analysis = engine.recognizeFromText("")
        assertTrue(analysis.recognized.not())
    }
}
