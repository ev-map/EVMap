package com.johan.evmap.fragment

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.johan.evmap.BuildConfig
import com.johan.evmap.MapsActivity
import com.johan.evmap.R


class AboutFragment : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById(R.id.toolbar) as Toolbar

        val navController = findNavController()
        toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about, rootKey)

        findPreference<Preference>("version")?.summary = BuildConfig.VERSION_NAME
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            "github_link" -> {
                (activity as? MapsActivity)?.openUrl(preference.summary.toString())
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

}