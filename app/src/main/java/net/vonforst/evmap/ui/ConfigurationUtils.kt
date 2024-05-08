package net.vonforst.evmap.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import net.vonforst.evmap.R
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

fun getAppLocale(context: Context): String? {
    val locales = AppCompatDelegate.getApplicationLocales()
    return if (locales.isEmpty) {
        "default"
    } else {
        val arr = Array(locales.size()) { locales.get(it)!!.toLanguageTag() }
        val choices =
            context.resources.getStringArray(R.array.pref_language_values).joinToString(",")
        LocaleListCompat.forLanguageTags(choices).getFirstMatch(arr)?.toLanguageTag()
    }
}
