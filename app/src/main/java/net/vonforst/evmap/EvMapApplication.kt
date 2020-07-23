package net.vonforst.evmap

import android.app.Application
import com.facebook.stetho.Stetho
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.updateNightMode

class EvMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        updateNightMode(PreferenceDataSource(this))
        Stetho.initializeWithDefaults(this);
        init(applicationContext)
    }
}