package com.smartai.adsmanager.screenrecognition

import android.graphics.Bitmap
import com.smartai.adsmanager.domain.ScreenAnalysis

/**
 * Screen Recognition Engine.
 *
 * Converts a captured screen frame into a [ScreenAnalysis] (Mining-screen recognition +
 * Loading ad detection). The default implementation uses ML Kit on-device OCR; tests use
 * a fake that feeds mock OCR text (no real advertisement impressions are used).
 */
interface ScreenRecognitionEngine {
    /**
     * Recognize a captured screen frame. A null bitmap yields an error analysis rather
     * than throwing — a missing/blank frame is never treated as a recognition result.
     */
    suspend fun recognizeFromBitmap(bitmap: Bitmap?): ScreenAnalysis
    fun recognizeFromText(text: String): ScreenAnalysis
}
