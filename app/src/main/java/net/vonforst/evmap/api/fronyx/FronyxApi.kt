package net.vonforst.evmap.api.fronyx

import android.content.Context
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import net.vonforst.evmap.BuildConfig
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FronyxApi {
    @GET("predictions/evse-id/{evseId}")
    suspend fun getPredictionsForEvseId(
        @Path("evseId") evseId: String,
        @Query("timeframe") timeframe: Int? = null
    ): FronyxEvseIdResponse

    companion object {
        private val cacheSize = 1L * 1024 * 1024 // 1MB

        private val moshi = Moshi.Builder()
            .add(ZonedDateTimeAdapter())
            .build()

        fun create(
            apikey: String,
            baseurl: String = "https://api.fronyx.io/api/",
            context: Context? = null
        ): FronyxApi {
            val client = OkHttpClient.Builder().apply {
                addInterceptor { chain ->
                    // add API key to every request
                    val original = chain.request()
                    val new = original.newBuilder()
                        .header("X-API-Token", apikey)
                        .header("Content-Type", "application/json")
                        .build()
                    chain.proceed(new)
                }
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(StethoInterceptor())
                }
                if (context != null) {
                    cache(Cache(context.getCacheDir(), cacheSize))
                }
            }.build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseurl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
            return retrofit.create(FronyxApi::class.java)
        }
    }
}