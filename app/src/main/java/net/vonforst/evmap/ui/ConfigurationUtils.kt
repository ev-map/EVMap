package net.vonforst.evmap.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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

fun getAppLocale(applicationContext: Context?): String? {
    AppCompatDelegate.getApplicationLocales()
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        "default"
    } else {
        return applicationContext?.getSystemService(LocaleManager::class.java).overrideLocaleConfig.supportedLocales.toString()

    }
}
