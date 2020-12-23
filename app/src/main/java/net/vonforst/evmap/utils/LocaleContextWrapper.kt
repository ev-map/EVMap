package net.vonforst.evmap.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import androidx.core.os.ConfigurationCompat
import java.util.*


class LocaleContextWrapper(base: Context?) : ContextWrapper(base) {
    companion object {
        fun wrap(context: Context, language: String): ContextWrapper {
            val sysConfig: Configuration = context.applicationContext.resources.configuration
            val appConfig: Configuration = context.resources.configuration

            if (language == "" || language == "default") {
                // set default locale
                Locale.setDefault(ConfigurationCompat.getLocales(sysConfig)[0])
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appConfig.setLocales(sysConfig.locales)
                } else {
                    @Suppress("DEPRECATION")
                    appConfig.locale = sysConfig.locale
                }
            } else {
                // set selected locale
                val locale = Locale(language)
                Locale.setDefault(locale)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appConfig.setLocale(locale)
                } else {
                    @Suppress("DEPRECATION")
                    appConfig.locale = locale
                }
            }

            return LocaleContextWrapper(context.createConfigurationContext(appConfig))
        }
    }
}