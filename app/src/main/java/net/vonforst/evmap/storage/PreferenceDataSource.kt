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
}