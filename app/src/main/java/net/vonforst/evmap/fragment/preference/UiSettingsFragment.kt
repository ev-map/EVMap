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
import com.github.erfansn.localeconfigx.configuredLocales
import net.vonforst.evmap.R
import net.vonforst.evmap.isAppInstalled
import net.vonforst.evmap.ui.getAppLocale
import net.vonforst.evmap.ui.map
import net.vonforst.evmap.ui.updateAppLocale
import net.vonforst.evmap.ui.updateNightMode

class UiSettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = false
    lateinit var langPref: ListPreference
    lateinit var immediateNavPref: CheckBoxPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_ui, rootKey)

        setupLangPref()

        val appLinkPref = findPreference<Preference>("applink_associate")!!
        appLinkPref.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        immediateNavPref = findPreference("navigate_use_maps")!!
        immediateNavPref.isVisible = isGoogleMapsInstalled()
    }

    private fun setupLangPref() {
        langPref = findPreference("language")!!
        val configuredLocales = requireContext().configuredLocales
        val numLocalesByLang = configuredLocales.map { it.language }.groupingBy { it }.eachCount()

        val localeNames = configuredLocales.map {
            val name = if (numLocalesByLang[it.language]!! > 1) {
                it.getDisplayName(it)
            } else {
                it.getDisplayLanguage(it)
            }
            name.replaceFirstChar { c -> c.uppercase(it) }
        }
        val localeTags = configuredLocales.map { it.toLanguageTag() }

        langPref.entries =
            (listOf(getString(R.string.pref_language_device_default)) + localeNames).toTypedArray()
        langPref.entryValues =
            (listOf("default") + localeTags).toTypedArray()
        langPref.setOnPreferenceChangeListener { _, newValue ->
            updateAppLocale(newValue as String)
            true
        }
    }

    private fun isGoogleMapsInstalled() =
        requireContext().packageManager.isAppInstalled("com.google.android.apps.maps")

    override fun onResume() {
        super.onResume()
        langPref.value = getAppLocale(requireContext())
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