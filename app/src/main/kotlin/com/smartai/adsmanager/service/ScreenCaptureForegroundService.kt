package com.smartai.adsmanager.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service that performs the *consented* screen monitoring.
 *
 * Started only after the user grants MediaProjection consent in [com.smartai.adsmanager.ui.MainActivity].
 * It captures the screen, runs on-device OCR via [MonitoringController], and keeps a
 * persistent foreground notification. It never interacts with other apps automatically
 * and stops cleanly when the user stops monitoring.
 *
 * CRITICAL (app-disappearing bug fix):
 *  - [startForeground] is called *first*, before any projection work, so the foreground
 *    service deadline is always satisfied and the process is never killed for it.
 *  - All projection work runs on a dedicated background thread and is fully
 *    exception-safe. A failure (SecurityException, invalid projection, VirtualDisplay
 *    error, …) is reported to the UI and the service stops gracefully — it never throws
 *    out of [onStartCommand], which would otherwise crash the whole app process and make
 *    the dashboard vanish back to the launcher.
 *  - The MediaProjection is never reused across sessions; each start requires fresh
 *    consent (the Activity always passes the just-granted result Intent).
 *  - Display changes are handled via [MonitoringController.handleDisplayChanged] (resize)
 *    instead of recreating a second projection.
 */
class ScreenCaptureForegroundService : Service() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationController.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring(intent)
            ACTION_STOP -> stopMonitoring()
            else -> stopSelf()
        }
        // MediaProjection cannot be silently recreated without fresh consent, so no
        // sticky restart. Recovery is handled by BootReceiver + the UI.
        return START_NOT_STICKY
    }

    private fun startMonitoring(intent: Intent) {
        // 1) Enter the foreground FIRST, with the monitoring notification. This guarantees
        //    the foreground-service deadline is met and the process stays alive regardless
        //    of what happens next during projection setup.
        val notification = NotificationController.buildMonitoringNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationController.NOTIF_MONITORING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NotificationController.NOTIF_MONITORING_ID, notification)
        }

        // 2) Do the (potentially throwing) projection work off the main thread.
        executor.execute {
            try {
                obtainProjectionAndStart(intent)
            } catch (e: Exception) {
                // NEVER let this propagate — it would crash the process and make the app
                // disappear. Report a useful error and stop cleanly.
                val message = "Screen capture failed to start: " +
                    (e.message ?: e.javaClass.simpleName)
                MonitoringController.onCaptureFailed(message)
                stopMonitoring()
            }
        }
    }

    private fun obtainProjectionAndStart(intent: Intent) {
        val projectionIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PROJECTION, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROJECTION) as? Intent
        }
        if (projectionIntent == null) {
            throw IllegalStateException("Missing projection consent Intent.")
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)

        val manager = getSystemService(MediaProjectionManager::class.java)
        val projection: MediaProjection = try {
            manager.getMediaProjection(resultCode, projectionIntent)
        } catch (e: SecurityException) {
            // Android 14+ can throw SecurityException if consent is invalid/expired.
            throw IllegalStateException(
                "MediaProjection consent was rejected or expired: ${e.message}",
                e,
            )
        } ?: throw IllegalStateException("MediaProjection not available — consent required.")

        val (width, height, densityDpi) = computeMetrics()

        // startCapture owns the full state transition (PERMISSION_GRANTED →
        // CAPTURE_INITIALIZING → CAPTURE_ACTIVE) and the capture pipeline.
        try {
            MonitoringController.startCapture(projection, width, height, densityDpi)
        } catch (e: Exception) {
            MonitoringController.onCaptureFailed(
                "Failed to initialize screen capture: ${e.message ?: e.javaClass.simpleName}",
            )
            releaseProjectionQuietly(projection)
            stopMonitoring()
            return
        }
    }

    private fun stopMonitoring() {
        MonitoringController.stop()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // API differences; ignore.
        }
        stopSelf()
    }

    /** Handle rotation / display changes by resizing the existing VirtualDisplay. */
    override fun onConfigurationChanged() {
        super.onConfigurationChanged()
        executor.execute {
            val (width, height, densityDpi) = computeMetrics()
            MonitoringController.resizeCapture(width, height, densityDpi)
        }
    }

    private fun computeMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    private fun releaseProjectionQuietly(projection: MediaProjection) {
        try {
            projection.stop()
        } catch (_: Exception) {
            // ignore
        }
    }

    override fun onDestroy() {
        MonitoringController.stop()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.smartai.adsmanager.action.START"
        const val ACTION_STOP = "com.smartai.adsmanager.action.STOP"
        const val EXTRA_PROJECTION = "projection_intent"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
