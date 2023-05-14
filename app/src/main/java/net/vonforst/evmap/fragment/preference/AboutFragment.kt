package net.vonforst.evmap.fragment.preference

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import com.mikepenz.aboutlibraries.LibsBuilder
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.storage.PreferenceDataSource


class AboutFragment : PreferenceFragmentCompat() {
    private lateinit var prefs: PreferenceDataSource
    private var developerOptionsCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        prefs = PreferenceDataSource(requireContext())

        // Workaround for AndroidX bug: https://github.com/material-components/material-components-android/issues/1984
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.windowBackground))
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about, rootKey)

        findPreference<Preference>("version")?.summary = BuildConfig.VERSION_NAME
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            "version" -> {
                if (!prefs.developerModeEnabled) {
                    developerOptionsCounter += 1
                    if (developerOptionsCounter >= 7) {
                        prefs.developerModeEnabled = true
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.developer_mode_enabled),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                true
            }

            "contributors" -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.about_contributors)
                    .setMessage(getString(R.string.about_contributors_text) + "\n\n" + getString(R.string.about_contributors_list))
                    .setPositiveButton(R.string.ok) { _, _ -> }
                    .show()
                true
            }

            "github_link" -> {
                (activity as? MapsActivity)?.openUrl(getString(R.string.github_link))
                true
            }
            "privacy" -> {
                (activity as? MapsActivity)?.openUrl(getString(R.string.privacy_link))
                true
            }
            "faq" -> {
                (activity as? MapsActivity)?.openUrl(getString(R.string.faq_link))
                true
            }
            "oss_licenses" -> {
                LibsBuilder()
                    .withLicenseShown(true)
                    .withAboutVersionShown(false)
                    .withAboutIconShown(false)
                    .withActivityTitle(getString(R.string.oss_licenses))
                    .withExcludedLibraries()
                    .start(requireActivity())
                true
            }
            "donate" -> {
                exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
                reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
                findNavController().navigate(R.id.action_about_to_donateFragment)
                true
            }
            "github_sponsors" -> {
                findNavController().navigate(R.id.action_about_to_github_sponsors)
                true
            }
            "twitter" -> {
                (activity as? MapsActivity)?.openUrl(getString(R.string.twitter_url))
                true
            }
            "goingelectric" -> {
                (activity as? MapsActivity)?.openUrl(getString(R.string.goingelectric_forum_url))
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

}