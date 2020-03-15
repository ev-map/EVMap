package com.johan.evmap

import android.app.Application
import com.facebook.stetho.Stetho

class EvMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this);
    }
}