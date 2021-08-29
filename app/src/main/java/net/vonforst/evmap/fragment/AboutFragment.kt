package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.mikepenz.aboutlibraries.LibsBuilder
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R


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