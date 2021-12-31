package net.vonforst.evmap.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import net.vonforst.evmap.R
import net.vonforst.evmap.ui.updateNightMode

class UiSettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_ui, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "language" -> {
                activity?.let {
                    it.finish();
                    it.startActivity(it.intent);
                }
            }
            "darkmode" -> {
                updateNightMode(prefs)
            }
        }
    }
}