package net.vonforst.evmap.storage

import android.content.Context
import androidx.preference.PreferenceManager
import net.vonforst.evmap.R
import net.vonforst.evmap.viewmodel.FILTERS_CUSTOM
import net.vonforst.evmap.viewmodel.FILTERS_DISABLED
import java.time.Instant

class PreferenceDataSource(val context: Context) {
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

    /**
     * Stores the current filtering status, which is either the ID of a filter profile or
     * one of FILTERS_DISABLED, FILTERS_CUSTOM
     */
    var filterStatus: Long
        get() =
            sp.getLong(
                "filter_status",
                // migration from versions before filter profiles were implemented
                if (sp.getBoolean("filters_active", true))
                    FILTERS_CUSTOM else FILTERS_DISABLED
            )
        set(value) {
            sp.edit().putLong("filter_status", value).apply()
        }

    /**
     * Stores the last filter profile which was selected
     * (excluding FILTERS_DISABLED, but including FILTERS_CUSTOM)
     */
    var lastFilterProfile: Long
        get() = sp.getLong("last_filter_profile", FILTERS_CUSTOM)
        set(value) {
            sp.edit().putLong("last_filter_profile", value).apply()
        }


    val language: String
        get() = sp.getString("language", "default")!!

    val darkmode: String
        get() = sp.getString("darkmode", "default")!!

    val mapProvider: String
        get() = sp.getString(
            "map_provider",
            context.getString(R.string.pref_map_provider_default)
        )!!
}