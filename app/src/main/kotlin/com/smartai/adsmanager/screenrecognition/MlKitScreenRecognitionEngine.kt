package com.smartai.adsmanager.screenrecognition

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartai.adsmanager.domain.DetectedElement
import com.smartai.adsmanager.domain.MiningScreenMatcher
import com.smartai.adsmanager.domain.ScreenAnalysis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * On-device, multi-strategy screen recognition using ML Kit Text Recognition.
 *
 * The agent does not rely on text matching alone — it also performs **visual element
 * detection**: every OCR text block is turned into a [DetectedElement] with its bounding
 * box and a button-like heuristic, so the agent can see *where* elements are on the
 * screen. The best-matching element for the Loading ad control is reported separately
 * so the live monitor can highlight it.
 *
 * Everything runs on-device. No frame or text leaves the device.
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
            val deferred = CompletableDeferred<List<Text.TextBlock>>()
            textRecognition.process(image)
                .addOnSuccessListener { deferred.complete(it.textBlocks) }
                .addOnFailureListener { deferred.completeExceptionally(it) }

            // Never block the frame loop indefinitely: a hung recognizer must not stall
            // monitoring. On timeout, report the error and keep the loop alive.
            val blocks = withTimeoutOrNull(ocrTimeoutMs) { deferred.await() }
            when {
                blocks == null ->
                    matcher.analyze("").copy(
                        error = "OCR timed out; the recognizer did not respond.",
                        ocrSucceeded = false,
                        pipelineError = "OCR timed out after ${ocrTimeoutMs}ms.",
                    )
                else ->
                    buildAnalysis(blocks, bitmap.width, bitmap.height)
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

    override fun recognizeFromText(text: String): ScreenAnalysis {
        // Text-only path (used by tests and fallbacks): no bounding boxes available.
        return matcher.analyze(text).copy(recognizedText = text, ocrSucceeded = true)
    }

    /**
     * Build a rich [ScreenAnalysis] from OCR text blocks: detect elements with bounds,
     * classify the screen, and locate the best-matching Loading ad element.
     */
    private fun buildAnalysis(blocks: List<Text.TextBlock>, screenWidth: Int, screenHeight: Int): ScreenAnalysis {
        val elements = blocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            toElement(block.text, box, screenWidth, screenHeight)
        }
        val fullText = blocks.joinToString(separator = " ") { it.text }
        val base = matcher.analyze(fullText)

        val targetElement = elements.firstOrNull { it.text.isTargetLoadingAd(matcher) }
        val classification = classify(base, elements)

        return base.copy(
            recognizedText = fullText,
            ocrSucceeded = true,
            detectedElements = elements,
            targetElement = targetElement,
            screenClassification = classification,
        )
    }

    /** Convert a text block into a [DetectedElement] with a button-like heuristic. */
    private fun toElement(
        text: String,
        box: Rect,
        screenWidth: Int,
        screenHeight: Int,
    ): DetectedElement {
        val normalized = text.trim()
        if (normalized.isEmpty()) return DetectedElement("", box.left, box.top, box.right, box.bottom)
        // A button-like element: compact relative to the screen and short text.
        val widthRatio = (box.width().toFloat() / screenWidth.coerceAtLeast(1))
        val heightRatio = (box.height().toFloat() / screenHeight.coerceAtLeast(1))
        val isButtonLike = widthRatio <= 0.5f && heightRatio <= 0.12f && normalized.split("\\s+".toRegex()).size <= 5
        return DetectedElement(
            text = normalized,
            left = box.left,
            top = box.top,
            right = box.right,
            bottom = box.bottom,
            isButtonLike = isButtonLike,
            confidence = 0.8,
        )
    }

    /** Classify the screen from the text analysis + detected elements. */
    private fun classify(base: ScreenAnalysis, elements: List<DetectedElement>): String {
        if (base.targetButtonAvailable) return "Mining — Loading ad available"
        if (base.recognized) {
            if (base.loadingIndicatorVisible) return "Mining — Loading in progress"
            return "Mining — Loading ad not available"
        }
        if (elements.any { it.isButtonLike }) return "Unknown screen with buttons"
        return "Unknown"
    }

    private fun String.isTargetLoadingAd(matcher: MiningScreenMatcher): Boolean =
        matcher.isTargetAvailable(this)

    fun close() {
        try {
            textRecognition.close()
        } catch (_: Exception) {
            // Best-effort release.
        }
    }
}
