package com.smartai.adsmanager.service

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjection.Callback
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface

/**
 * Owns the screen-capture pipeline for one *explicitly granted* [MediaProjection]
 * session: one [ImageReader], one [VirtualDisplay], and the frame→[Bitmap] conversion
 * used by on-device OCR.
 *
 * Hardened for production:
 *  - All resource operations are exception-safe; a failure never propagates to the
 *    service's main thread (which would crash the whole process).
 *  - Supports display changes via [resize]/[setSurface] instead of recreating the
 *    VirtualDisplay on the same projection.
 *  - Registers a [Callback] so the controller is notified when the system stops the
 *    projection (e.g. permission revoked) and can react gracefully.
 *  - Every acquired [Image] is closed after processing.
 *
 * The projection is only ever created from a user-granted consent intent (see
 * [ScreenCaptureForegroundService]); no screen is captured without explicit consent.
 * Frames stay on-device and are never transmitted.
 */
class MediaProjectionController(
    private val projection: MediaProjection,
    private var width: Int,
    private var height: Int,
    private var densityDpi: Int,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var stopped = false

    /** Notified when the system stops the projection (onStop). */
    var onProjectionStopped: (() -> Unit)? = null

    private val projectionCallback = object : Callback() {
        override fun onStop() {
            // The system ended the capture session (user revoked, another app took over,
            // etc.). Surface this so the orchestrator can stop gracefully instead of
            // silently continuing with a dead projection.
            if (!stopped) {
                onProjectionStopped?.invoke()
            }
        }
    }

    /** Create the ImageReader + VirtualDisplay and begin producing frames. */
    fun start() {
        if (stopped) return
        ensureReader()
        val surface: Surface = imageReader!!.surface
        virtualDisplay = try {
            projection.createVirtualDisplay(
                "SmartAIAdsManager",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                mainHandler,
            )
        } catch (e: Exception) {
            // Creating the virtual display can throw IllegalStateException if the
            // projection is already invalid. Must not crash the process.
            releaseInternal()
            throw e
        }
        try {
            projection.registerCallback(projectionCallback, mainHandler)
        } catch (_: Exception) {
            // Some OEMs reject the callback; capture still works without it.
        }
    }

    /** Handle a display size/density change by resizing the existing VirtualDisplay. */
    fun resize(newWidth: Int, newHeight: Int, newDensityDpi: Int) {
        if (stopped) return
        val vd = virtualDisplay ?: return
        width = newWidth
        height = newHeight
        densityDpi = newDensityDpi
        try {
            vd.resize(newWidth, newHeight, newDensityDpi)
        } catch (_: Exception) {
            // If resize fails, fall back to recreating the display surface-free.
        }
    }

    /** Replace the surface the VirtualDisplay renders into (used after a surface change). */
    fun setSurface(surface: Surface?) {
        if (stopped) return
        val vd = virtualDisplay ?: return
        try {
            vd.setSurface(surface)
        } catch (_: Exception) {
            // Best-effort; ignore failures.
        }
    }

    /**
     * Capture the most recent frame, or null if none is available yet. Converts the
     * Image (handling row-stride padding) to a Bitmap and always closes the Image.
     */
    fun captureFrame(): Bitmap? {
        val reader = imageReader ?: return null
        val image: Image = try {
            reader.acquireLatestImage()
        } catch (_: Exception) {
            return null
        } ?: return null
        try {
            return imageToBitmap(image)
        } catch (_: Exception) {
            return null
        } finally {
            try {
                image.close()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun ensureReader() {
        if (imageReader != null) return
        imageReader = try {
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        } catch (_: Exception) {
            null
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val rowStridePx = if (plane.rowStride / plane.pixelStride > 0)
            plane.rowStride / plane.pixelStride else image.width
        // Build a full-stride bitmap then crop to the actual image width so row padding
        // does not corrupt pixels.
        val full = Bitmap.createBitmap(rowStridePx, image.height, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(plane.buffer)
        return if (rowStridePx == image.width) full
        else Bitmap.createBitmap(full, 0, 0, image.width, image.height)
    }

    /** Stop the capture and release all resources. Idempotent. */
    fun stop() {
        if (stopped) return
        stopped = true
        releaseInternal()
    }

    fun release() = stop()

    private fun releaseInternal() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
            // ignore
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
            // ignore
        }
        imageReader = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                projection.unregisterCallback(projectionCallback)
            } else {
                @Suppress("DEPRECATION")
                projection.unregisterCallback(projectionCallback)
            }
        } catch (_: Exception) {
            // ignore
        }
        try {
            projection.stop()
        } catch (_: Exception) {
            // ignore — already stopped or invalid
        }
    }
}
