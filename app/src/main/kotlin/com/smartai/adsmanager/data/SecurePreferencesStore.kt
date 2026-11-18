package com.smartai.adsmanager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.smartai.adsmanager.domain.SelectionStore

/**
 * Secure local storage for sensitive, non-reward data (the selected/connected app
 * package name, consent state, and user settings). Uses [EncryptedSharedPreferences]
 * backed by the Android Keystore. Falls back to plain SharedPreferences only if the
 * crypto layer is unavailable, and reports that via [isEncrypted].
 */
class SecurePreferencesStore(context: Context) : SelectionStore {

    private val delegate: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "smartai_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        context.getSharedPreferences("smartai_fallback_prefs", Context.MODE_PRIVATE)
    }

    val isEncrypted: Boolean = delegate is EncryptedSharedPreferences

    // --- Selected / connected app -----------------------------------------------

    override fun getSelectedPackage(): String? = delegate.getString(KEY_SELECTED_PACKAGE, null)

    override fun setSelectedPackage(packageName: String?) {
        val editor = delegate.edit()
        if (packageName == null) {
            editor.remove(KEY_SELECTED_PACKAGE)
        } else {
            editor.putString(KEY_SELECTED_PACKAGE, packageName)
        }
        editor.apply()
    }

    // --- Consent ----------------------------------------------------------------

    fun hasConsented(): Boolean = delegate.getBoolean(KEY_CONSENT_GRANTED, false)

    fun setConsent(granted: Boolean) {
        delegate.edit().putBoolean(KEY_CONSENT_GRANTED, granted).apply()
    }

    // --- User settings ---------------------------------------------------------

    fun getLoadingTimeoutMs(): Long = delegate.getLong(KEY_LOADING_TIMEOUT_MS, 90_000L)

    fun setLoadingTimeoutMs(ms: Long) {
        delegate.edit().putLong(KEY_LOADING_TIMEOUT_MS, ms).apply()
    }

    // --- Data deletion ----------------------------------------------------------

    fun clearAll() {
        delegate.edit().clear().apply()
    }

    companion object {
        private const val KEY_SELECTED_PACKAGE = "selected_package"
        private const val KEY_CONSENT_GRANTED = "consent_granted"
        private const val KEY_LOADING_TIMEOUT_MS = "loading_timeout_ms"
    }
}
