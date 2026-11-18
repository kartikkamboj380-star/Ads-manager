package com.smartai.adsmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.smartai.adsmanager.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restart recovery: after boot / app-replace, if a monitoring session was recorded as
 * ACTIVE but the process is no longer running it, notify the user so they can review and
 * resume. No monitoring is silently restarted (Android background limits + the fact that
 * MediaProjection consent cannot be recreated without the user).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        NotificationController.createChannels(context)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val db = AppDatabase.get(context)
            val activeSession = db.sessionDao().activeSession()
            if (activeSession != null) {
                val notification = NotificationController.buildRecoveryNotification(context)
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.notify(NotificationController.NOTIF_RECOVERY_ID, notification)
            }
        }
    }
}
