package net.vonforst.evmap.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import net.vonforst.evmap.R
import net.vonforst.evmap.ui.RangeSliderPreference
import java.text.NumberFormat

class AndroidAutoSettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = false

    private lateinit var rangePreference: RangeSliderPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rangePreference = findPreference("chargeprice_battery_range_android_auto")!!
        rangePreference.labelFormatter = { value: Float ->
            val fmt = NumberFormat.getNumberInstance()
            fmt.maximumFractionDigits = 0
            fmt.format(value.toDouble()) + "%"
        }
        updateRangePreferenceSummary()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_android_auto, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "chargeprice_battery_range_android_auto_min", "chargeprice_battery_range_android_auto_max" -> {
                updateRangePreferenceSummary()
            }
        }
    }

    private fun updateRangePreferenceSummary() {
        val range = prefs.chargepriceBatteryRangeAndroidAuto
        rangePreference.summary = getString(R.string.chargeprice_battery_range, range[0], range[1])
    }
}