package net.vonforst.evmap.fragment.preference

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import net.vonforst.evmap.R
import net.vonforst.evmap.isAppInstalled
import net.vonforst.evmap.ui.getAppLocale
import net.vonforst.evmap.ui.updateAppLocale
import net.vonforst.evmap.ui.updateNightMode

class UiSettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = false
    lateinit var langPref: ListPreference
    lateinit var immediateNavPref: CheckBoxPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_ui, rootKey)

        langPref = findPreference("language")!!
        langPref.setOnPreferenceChangeListener { _, newValue ->
            updateAppLocale(newValue as String)
            true
        }

        val appLinkPref = findPreference<Preference>("applink_associate")!!
        appLinkPref.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        immediateNavPref = findPreference("navigate_use_maps")!!
        immediateNavPref.isVisible = isGoogleMapsInstalled()
    }

    private fun isGoogleMapsInstalled() =
        requireContext().packageManager.isAppInstalled("com.google.android.apps.maps")

    override fun onResume() {
        super.onResume()
        langPref.value = getAppLocale(context)
        immediateNavPref.isVisible = isGoogleMapsInstalled()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "darkmode" -> {
                updateNightMode(prefs)
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "applink_associate" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val context = context ?: return false
                    val intent = Intent(
                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}