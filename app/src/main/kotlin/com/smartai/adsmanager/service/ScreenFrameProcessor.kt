package com.smartai.adsmanager.service

import android.graphics.Bitmap
import com.smartai.adsmanager.domain.Clock
import com.smartai.adsmanager.domain.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Processes screen frames from a [MediaProjectionController] on a dedicated background
 * dispatcher.
 *
 * Responsibilities:
 *  - Polls the controller at a fixed cadence and hands each valid frame to [onFrame].
 *  - Tracks the [lastFrameTimeEpochMs] of the most recent *valid* frame so the UI can
 *    show "last successful frame time" and detect a frozen/dead capture.
 *  - Treats blank/zero-size frames as invalid (they are never processed and never
 *    treated as proof of anything).
 *  - Is fully exception-safe: a throwing [onFrame] cancels only this processor, never
 *    the host process.
 *
 * The processor is owned by [MonitoringController] and stopped when monitoring stops.
 */
class ScreenFrameProcessor(
    private val clock: Clock = SystemClock(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** Epoch-ms of the last valid frame processed, or 0 if none yet. */
    var lastFrameTimeEpochMs: Long = 0L
        private set

    val isRunning: Boolean
        get() = job?.isActive == true

    /**
     * Start polling [controller]. [onFrame] is invoked on a background dispatcher for
     * each valid frame; it may suspend (e.g. to run on-device OCR).
     */
    fun start(
        controller: MediaProjectionController,
        intervalMs: Long,
        onFrame: suspend (Bitmap) -> Unit,
    ) {
        if (isRunning) return
        lastFrameTimeEpochMs = 0L
        job = scope.launch {
            while (isActive) {
                val frame = controller.captureFrame()
                if (frame != null && frame.width > 0 && frame.height > 0) {
                    lastFrameTimeEpochMs = clock.now()
                    try {
                        onFrame(frame)
                    } catch (_: Exception) {
                        // A bad frame/OCR failure must not kill the loop or the process.
                    }
                }
                delay(intervalMs)
            }
        }
    }

    /** Stop polling. Idempotent. Does not release the controller (caller owns it). */
    fun stop() {
        job?.cancel()
        job = null
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
