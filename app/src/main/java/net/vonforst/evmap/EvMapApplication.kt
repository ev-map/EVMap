package net.vonforst.evmap

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.work.*
import net.vonforst.evmap.storage.CleanupCacheWorker
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.updateAppLocale
import net.vonforst.evmap.ui.updateNightMode
import org.acra.config.dialog
import org.acra.config.httpSender
import org.acra.config.limiter
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import java.time.Duration

class EvMapApplication : Application(), Configuration.Provider {
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

        init(applicationContext)
        addDebugInterceptors(applicationContext)

        if (!BuildConfig.DEBUG) {
            initAcra {
                buildConfigClass = BuildConfig::class.java

                if (BuildConfig.FLAVOR_automotive == "automotive") {
                    // Vehicles often don't have an email app, so use HTTP to send instead
                    reportFormat = StringFormat.JSON
                    httpSender {
                        uri = getString(R.string.acra_backend_url)
                        val creds = getString(R.string.acra_credentials).split(":")
                        basicAuthLogin = creds[0]
                        basicAuthPassword = creds[1]
                        httpMethod = HttpSender.Method.POST
                    }
                } else {
                    reportFormat = StringFormat.KEY_VALUE_LIST
                    mailSender {
                        mailTo = "evmap+crashreport@vonforst.net"
                    }
                }

                dialog {
                    text = getString(R.string.crash_report_text)
                    title = getString(R.string.app_name)
                    commentPrompt = getString(R.string.crash_report_comment_prompt)
                    resIcon = R.drawable.ic_launcher_foreground
                    resTheme = R.style.AppTheme
                    if (BuildConfig.FLAVOR_automotive == "automotive") {
                        reportDialogClass =
                            Class.forName("androidx.car.app.activity.CarAppActivity") as Class<out Activity>?
                    }
                }

                limiter {
                    enabled = true
                }
            }
        }

        val cleanupCacheRequest = PeriodicWorkRequestBuilder<CleanupCacheWorker>(Duration.ofDays(1))
            .setConstraints(Constraints.Builder().apply {
                setRequiresBatteryNotLow(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setRequiresDeviceIdle(true)
                }
            }.build()).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CleanupCacheWorker", ExistingPeriodicWorkPolicy.REPLACE, cleanupCacheRequest
        )
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder().build()
    }
}