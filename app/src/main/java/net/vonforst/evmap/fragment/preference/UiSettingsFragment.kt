package net.vonforst.evmap.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import net.vonforst.evmap.R
import net.vonforst.evmap.ui.getAppLocale
import net.vonforst.evmap.ui.updateAppLocale
import net.vonforst.evmap.ui.updateNightMode

class UiSettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = false
    lateinit var langPref: ListPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_ui, rootKey)

        langPref = findPreference("language")!!
        langPref.setOnPreferenceChangeListener { _, newValue ->
            updateAppLocale(newValue as String)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        langPref.value = getAppLocale()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "darkmode" -> {
                updateNightMode(prefs)
            }
        }
    }
}