package com.smartai.adsmanager.screenrecognition

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartai.adsmanager.domain.MiningScreenMatcher
import com.smartai.adsmanager.domain.ScreenAnalysis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * On-device OCR-based screen recognition using ML Kit Text Recognition.
 *
 * OCR runs entirely on-device. The recognized text is passed to [MiningScreenMatcher],
 * which determines whether the Mining screen is present and whether the exact "Loading
 * ad" control is available. No frame or text leaves the device.
 *
 * Robustness: the ML Kit call is awaited with a hard timeout. If the recognizer ever
 * fails to invoke its listeners (a known edge case on some OEMs / under load), the
 * timeout releases the frame loop so monitoring continues instead of stalling forever.
 */
class MlKitScreenRecognitionEngine(
    private val matcher: MiningScreenMatcher = MiningScreenMatcher(),
    /** Hard limit for a single OCR call; on expiry the frame loop is released. */
    private val ocrTimeoutMs: Long = 4_000L,
) : ScreenRecognitionEngine {

    private val textRecognition = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeFromBitmap(bitmap: Bitmap?): ScreenAnalysis {
        if (bitmap == null) {
            return matcher.analyze("").copy(
                error = "No frame available to recognize.",
                pipelineError = "No frame available from the capture pipeline.",
            )
        }
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val deferred = CompletableDeferred<String>()
            textRecognition.process(image)
                .addOnSuccessListener { deferred.complete(it.text) }
                .addOnFailureListener { deferred.completeExceptionally(it) }

            // Never block the frame loop indefinitely: a hung recognizer must not stall
            // monitoring. On timeout, report the error and keep the loop alive.
            val text = withTimeoutOrNull(ocrTimeoutMs) { deferred.await() }
            when {
                text == null ->
                    matcher.analyze("").copy(
                        error = "OCR timed out; the recognizer did not respond.",
                        ocrSucceeded = false,
                        pipelineError = "OCR timed out after ${ocrTimeoutMs}ms.",
                    )
                else ->
                    matcher.analyze(text).copy(
                        recognizedText = text,
                        ocrSucceeded = true,
                    )
            }
        } catch (e: Exception) {
            // OCR failure must not crash monitoring; report an analysis with the error.
            matcher.analyze("").copy(
                error = "OCR failed: ${e.message ?: "unknown"}",
                ocrSucceeded = false,
                pipelineError = "OCR exception: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    override fun recognizeFromText(text: String): ScreenAnalysis =
        matcher.analyze(text).copy(recognizedText = text, ocrSucceeded = true)

    fun close() {
        try {
            textRecognition.close()
        } catch (_: Exception) {
            // Best-effort release.
        }
    }
}
