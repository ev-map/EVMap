package net.vonforst.evmap

import android.content.Context
import okhttp3.OkHttpClient

fun addDebugInterceptors(context: Context) {

}

fun OkHttpClient.Builder.addDebugInterceptors(): OkHttpClient.Builder = this