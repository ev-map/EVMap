package net.vonforst.evmap.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.github.erfansn.localeconfigx.configuredLocales
import net.vonforst.evmap.storage.PreferenceDataSource
import java.util.Locale


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
        val choices = context.configuredLocales
        choices.getFirstMatch(arr)?.toLanguageTag()
    }
}

inline fun <R> LocaleListCompat.map(transform: (Locale) -> R): List<R> = List(size()) {
    transform(get(it)!!)
}