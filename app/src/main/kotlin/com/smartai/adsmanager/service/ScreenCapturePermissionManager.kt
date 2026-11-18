package com.smartai.adsmanager.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

/**
 * Encapsulates the Android MediaProjection screen-capture consent flow.
 *
 * The official [MediaProjectionManager.createScreenCaptureIntent] API is the only way to
 * obtain screen-capture consent. This manager builds that intent and interprets the
 * Activity-Result so the rest of the app never touches the raw consent mechanics.
 *
 * Important: a consent Intent is valid only for the capture session it was granted for.
 * It must never be reused for a later session — each new session requires a fresh
 * [createScreenCaptureIntent] call and a fresh user grant. This manager always produces
 * a fresh intent.
 */
class ScreenCapturePermissionManager {

    /** Build a fresh screen-capture consent intent (never cached/reused). */
    fun createCaptureIntent(context: Context): Intent {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        return mgr.createScreenCaptureIntent()
    }

    /**
     * Interpret the Activity-Result. Returns the granted consent [Intent] on success, or
     * null if the user denied or the result was malformed.
     */
    fun interpretResult(resultCode: Int, data: Intent?): Intent? {
        if (resultCode != Activity.RESULT_OK) return null
        return data
    }
}
