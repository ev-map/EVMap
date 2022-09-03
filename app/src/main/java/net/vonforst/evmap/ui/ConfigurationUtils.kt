package net.vonforst.evmap.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.storage.PreferenceDataSource

fun updateNightMode(prefs: PreferenceDataSource) {
    AppCompatDelegate.setDefaultNightMode(
        when (prefs.darkmode) {
            "on" -> AppCompatDelegate.MODE_NIGHT_YES
            "off" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}

fun updateAppLocale(language: String) {
    AppCompatDelegate.setApplicationLocales(
        if (language in listOf("", "default")) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language)
        }
    )
}

fun getAppLocale(): String? {
    val locales = AppCompatDelegate.getApplicationLocales()
    return if (locales.isEmpty) {
        "default"
    } else {
        val arr = Array(locales.size()) { locales.get(it)!!.toLanguageTag() }
        LocaleListCompat.forLanguageTags(BuildConfig.supportedLocales).getFirstMatch(arr)
            ?.toLanguageTag()
    }
}