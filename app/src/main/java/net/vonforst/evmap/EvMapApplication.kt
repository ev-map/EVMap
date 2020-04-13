package net.vonforst.evmap

import android.app.Application
import com.facebook.stetho.Stetho
import com.google.android.libraries.places.api.Places

class EvMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this);
        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
    }
}