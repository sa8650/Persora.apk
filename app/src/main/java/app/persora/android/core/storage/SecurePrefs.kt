package app.persora.android.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android Keystore-backed preferences. Holds the session cookie, app-lock flag and onboarding state.
 * Never written to backups (see data_extraction_rules.xml).
 */
class SecurePrefs(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "persora_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (error: Exception) {
        // Keystore corruption (rare, e.g. after a restore). Start clean rather than crash-loop.
        context.deleteSharedPreferences("persora_secure")
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "persora_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getString(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String?) = prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    fun getBoolean(key: String, default: Boolean = false) = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun remove(key: String) = prefs.edit().remove(key).apply()
    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        const val KEY_COOKIES = "cookies"
        const val KEY_ONBOARDED = "onboarding_complete"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_LAST_USER = "last_user_json"
        const val KEY_PRIMARY_INTEREST = "onboarding_interest"
        const val KEY_CHECKLIST_DISMISSED = "checklist_dismissed"
    }
}
