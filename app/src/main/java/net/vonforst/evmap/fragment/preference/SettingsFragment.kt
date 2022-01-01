package net.vonforst.evmap.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import net.vonforst.evmap.R


class SettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)
        addPreferencesFromResource(R.xml.settings_variantspecific)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {

    }
}