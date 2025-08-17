package net.vonforst.evmap

import android.content.Context
import android.os.Build
import okhttp3.OkHttpClient
import timber.log.Timber

fun addDebugInterceptors(context: Context) {
    if (Build.FINGERPRINT == "robolectric") return

    Timber.plant(Timber.DebugTree())
}

fun OkHttpClient.Builder.addDebugInterceptors(): OkHttpClient.Builder {
    return this
}