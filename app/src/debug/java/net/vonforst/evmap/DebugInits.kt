package net.vonforst.evmap

import android.content.Context
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.plugins.databases.DatabasesFlipperPlugin
import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin
import com.facebook.flipper.plugins.network.FlipperOkhttpInterceptor
import com.facebook.flipper.plugins.network.NetworkFlipperPlugin
import com.facebook.flipper.plugins.sharedpreferences.SharedPreferencesFlipperPlugin
import com.facebook.soloader.SoLoader
import okhttp3.OkHttpClient

private val networkFlipperPlugin = NetworkFlipperPlugin()

fun addDebugInterceptors(context: Context) {
    SoLoader.init(context, false)
    val client = AndroidFlipperClient.getInstance(context)
    client.addPlugin(InspectorFlipperPlugin(context, DescriptorMapping.withDefaults()))
    client.addPlugin(networkFlipperPlugin)
    client.addPlugin(DatabasesFlipperPlugin(context))
    client.addPlugin(SharedPreferencesFlipperPlugin(context))
    client.start()
}

fun OkHttpClient.Builder.addDebugInterceptors(): OkHttpClient.Builder {
    this.addNetworkInterceptor(FlipperOkhttpInterceptor(networkFlipperPlugin))
    return this
}