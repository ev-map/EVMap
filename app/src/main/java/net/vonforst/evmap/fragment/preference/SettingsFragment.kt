package net.vonforst.evmap.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import net.vonforst.evmap.R


class SettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)
        addPreferencesFromResource(R.xml.settings_variantspecific)
        findPreference<Preference>("developer_options")?.isVisible = prefs.developerModeEnabled
    }

    override fun onResume() {
        super.onResume()
        findPreference<Preference>("developer_options")?.isVisible = prefs.developerModeEnabled
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {

    }
}