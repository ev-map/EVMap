package net.vonforst.evmap.api.openchargemap

import android.content.Context
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import net.vonforst.evmap.BuildConfig
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenChargeMapApi {
    @GET("poi/")
    suspend fun getChargepoints(
        @Query("boundingbox") boundingbox: OCMBoundingBox,
        @Query("connectiontypeid") plugs: String? = null,
        @Query("minpowerkw") minPower: Double? = null,
        @Query("compact") compact: Boolean = true,
        @Query("maxresults") maxresults: Int = 100
    ): Response<List<OCMChargepoint>>

    @GET("poi/")
    suspend fun getChargepointDetail(
        @Query("chargepointid") id: Long,
        @Query("compact") compact: Boolean = false
    ): Response<List<OCMChargepoint>>

    /*
    @GET("referencedata/")
    suspend fun getReferenceData(): Response<OCMReferenceData>
     */

    companion object {
        private val cacheSize = 10L * 1024 * 1024 // 10MB

        val moshi = Moshi.Builder()
            .add(ZonedDateTimeAdapter())
            .build()

        fun create(
            apikey: String,
            baseurl: String = "https://api.openchargemap.io/v3",
            context: Context? = null
        ): OpenChargeMapApi {
            val client = OkHttpClient.Builder().apply {
                addInterceptor { chain ->
                    // add API key to every request
                    val original = chain.request()
                    val new = original.newBuilder()
                        .header("X-API-Key", apikey)
                        .build()
                    chain.proceed(new)
                }
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(StethoInterceptor())
                }
                if (context != null) {
                    cache(Cache(context.cacheDir, cacheSize))
                }
            }.build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseurl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
            return retrofit.create(OpenChargeMapApi::class.java)
        }
    }
}
