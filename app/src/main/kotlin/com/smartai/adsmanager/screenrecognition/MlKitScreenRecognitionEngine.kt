package com.smartai.adsmanager.screenrecognition

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartai.adsmanager.domain.MiningScreenMatcher
import com.smartai.adsmanager.domain.ScreenAnalysis
import kotlinx.coroutines.CompletableDeferred

/**
 * On-device OCR-based screen recognition using ML Kit Text Recognition.
 *
 * OCR runs entirely on-device. The recognized text is passed to [MiningScreenMatcher],
 * which determines whether the Mining screen is present and whether the exact "Loading
 * ad" control is available. No frame or text leaves the device.
 */
class MlKitScreenRecognitionEngine(
    private val matcher: MiningScreenMatcher = MiningScreenMatcher(),
) : ScreenRecognitionEngine {

    private val textRecognition = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeFromBitmap(bitmap: Bitmap?): ScreenAnalysis {
        if (bitmap == null) {
            return matcher.analyze("").copy(error = "No frame available to recognize.")
        }
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val deferred = CompletableDeferred<String>()
            textRecognition.process(image)
                .addOnSuccessListener { deferred.complete(it.text) }
                .addOnFailureListener { deferred.completeExceptionally(it) }
            recognizeFromText(deferred.await())
        } catch (e: Exception) {
            // OCR failure must not crash monitoring; report an analysis with the error.
            matcher.analyze("").copy(error = "OCR failed: ${e.message ?: "unknown"}")
        }
    }

    override fun recognizeFromText(text: String): ScreenAnalysis =
        matcher.analyze(text).copy(recognizedText = text)

    fun close() {
        try {
            textRecognition.close()
        } catch (_: Exception) {
            // Best-effort release.
        }
    }
}
