package net.vonforst.evmap

import android.app.Application
import com.facebook.stetho.Stetho
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.updateAppLocale
import net.vonforst.evmap.ui.updateNightMode
import org.acra.config.dialog
import org.acra.config.limiter
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra

class EvMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = PreferenceDataSource(this)
        updateNightMode(prefs)

        // Convert to new AppCompat storage for app language
        val lang = prefs.language
        if (lang != null && lang !in listOf("", "default")) {
            updateAppLocale(lang)
            prefs.language = null
        }

        Stetho.initializeWithDefaults(this);
        init(applicationContext)

        if (!BuildConfig.DEBUG) {
            initAcra {
                buildConfigClass = BuildConfig::class.java
                reportFormat = StringFormat.KEY_VALUE_LIST

                mailSender {
                    mailTo = "evmap+crashreport@vonforst.net"
                }

                dialog {
                    text = getString(R.string.crash_report_text)
                    title = getString(R.string.app_name)
                    commentPrompt = getString(R.string.crash_report_comment_prompt)
                    resIcon = R.drawable.ic_launcher_foreground
                    resTheme = R.style.AppTheme
                }

                limiter {
                    enabled = true
                }
            }
        }
    }
}