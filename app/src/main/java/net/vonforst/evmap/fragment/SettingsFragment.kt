package net.vonforst.evmap.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.storage.PreferenceDataSource


class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: PreferenceDataSource

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById(R.id.toolbar) as Toolbar
        prefs = PreferenceDataSource(requireContext())

        val navController = findNavController()
        toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            else -> super.onPreferenceTreeClick(preference)
        }
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
                AppCompatDelegate.setDefaultNightMode(
                    when (prefs.darkmode) {
                        "on" -> AppCompatDelegate.MODE_NIGHT_YES
                        "off" -> AppCompatDelegate.MODE_NIGHT_NO
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

}