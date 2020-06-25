package net.vonforst.evmap.storage

import android.content.Context
import androidx.preference.PreferenceManager
import java.time.Instant

class PreferenceDataSource(context: Context) {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)

    var navigateUseMaps: Boolean
        get() = sp.getBoolean("navigate_use_maps", true)
        set(value) {
            sp.edit().putBoolean("navigate_use_maps", value).apply()
        }

    var lastPlugUpdate: Instant
        get() = Instant.ofEpochMilli(sp.getLong("last_plug_update", 0L))
        set(value) {
            sp.edit().putLong("last_plug_update", value.toEpochMilli()).apply()
        }

    var lastNetworkUpdate: Instant
        get() = Instant.ofEpochMilli(sp.getLong("last_network_update", 0L))
        set(value) {
            sp.edit().putLong("last_network_update", value.toEpochMilli()).apply()
        }

    var lastChargeCardUpdate: Instant
        get() = Instant.ofEpochMilli(sp.getLong("last_chargecard_update", 0L))
        set(value) {
            sp.edit().putLong("last_chargecard_update", value.toEpochMilli()).apply()
        }

    var filtersActive: Boolean
        get() = sp.getBoolean("filters_active", true)
        set(value) {
            sp.edit().putBoolean("filters_active", value).apply()
        }

    val language: String
        get() = sp.getString("language", "default")!!

    val darkmode: String
        get() = sp.getString("darkmode", "default")!!
}