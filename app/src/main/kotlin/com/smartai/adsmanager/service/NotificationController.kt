package com.smartai.adsmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartai.adsmanager.R
import com.smartai.adsmanager.ui.MainActivity

/**
 * Builds the foreground-service notifications for the monitoring agent.
 *
 * The monitoring notification is persistent ([setOngoing]) and exposes:
 *  - a content intent that **Reopens the Dashboard**, and
 *  - a **Stop Agent** action that stops the foreground service.
 * It clearly states the agent is running and capture is active.
 */
object NotificationController {

    const val CHANNEL_MONITORING = "monitoring"
    const val CHANNEL_REWARD = "reward"
    const val NOTIF_MONITORING_ID = 1001
    const val NOTIF_RECOVERY_ID = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val monitoring = NotificationChannel(
                CHANNEL_MONITORING,
                context.getString(R.string.channel_monitoring_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_monitoring_desc)
                setShowBadge(false)
            }
            val reward = NotificationChannel(
                CHANNEL_REWARD,
                context.getString(R.string.channel_reward_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_reward_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannels(listOf(monitoring, reward))
        }
    }

    /** PendingIntent that reopens the dashboard. */
    fun buildDashboardIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** PendingIntent that tells the foreground service to stop. */
    fun buildStopServiceIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScreenCaptureForegroundService::class.java).apply {
            action = ScreenCaptureForegroundService.ACTION_STOP
        }
        return PendingIntent.getService(
            context,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Persistent foreground notification shown while the agent monitors. Includes the
     * "Reopen Dashboard" content intent and the "Stop Agent" action.
     */
    fun buildMonitoringNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_monitoring_title))
            .setContentText(context.getString(R.string.notification_monitoring_text))
            .setContentIntent(buildDashboardIntent(context))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                0,
                context.getString(R.string.notification_monitoring_stop),
                buildStopServiceIntent(context),
            )
            .addAction(
                0,
                context.getString(R.string.notification_reopen_dashboard),
                buildDashboardIntent(context),
            )
            .build()
    }

    /** Temporary (auto-cancel) notification used for restart-recovery prompts. */
    fun buildRecoveryNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_recovery_title))
            .setContentText(context.getString(R.string.notification_recovery_text))
            .setContentIntent(buildDashboardIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
