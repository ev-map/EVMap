package net.vonforst.evmap.storage

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKeys
import net.vonforst.evmap.api.availability.TeslaAvailabilityDetector

/**
 * Encrypted data storage for sensitive data such as API access tokens.
 * This will not be included in backups.
 */
class EncryptedPreferenceDataStore(context: Context) : TeslaAvailabilityDetector.TokenStore {
    val sp = EncryptedSharedPreferences.create(
        context,
        "encrypted_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override var teslaRefreshToken: String?
        get() = sp.getString(
            "tesla_refresh_token", null
        )
        set(value) {
            sp.edit().putString("tesla_refresh_token", value).apply()
        }
    override var teslaAccessToken: String?
        get() = sp.getString("tesla_access_token", null)
        set(value) {
            sp.edit().putString("tesla_access_token", value).apply()
        }
    override var teslaAccessTokenExpiry: Long
        get() = sp.getLong("tesla_access_token_expiry", -1)
        set(value) {
            sp.edit().putLong("tesla_access_token_expiry", value).apply()
        }

    var teslaEmail: String?
        get() = sp.getString("tesla_email", null)
        set(value) {
            sp.edit().putString("tesla_email", value).apply()
        }
}