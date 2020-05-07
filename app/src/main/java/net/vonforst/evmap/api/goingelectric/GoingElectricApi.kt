package net.vonforst.evmap.api.goingelectric

import android.content.Context
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface GoingElectricApi {
    @GET("chargepoints/")
    fun getChargepoints(
        @Query("sw_lat") swlat: Double, @Query("sw_lng") sw_lng: Double,
        @Query("ne_lat") ne_lat: Double, @Query("ne_lng") ne_lng: Double,
        @Query("clustering") clustering: Boolean,
        @Query("zoom") zoom: Float,
        @Query("cluster_distance") clusterDistance: Int,
        @Query("freecharging") freecharging: Boolean,
        @Query("freeparking") freeparking: Boolean,
        @Query("min_power") minPower: Int
    ): Call<ChargepointList>

    @GET("chargepoints/")
    fun getChargepointDetail(@Query("ge_id") id: Long): Call<ChargepointList>

    companion object {
        private val cacheSize = 10L * 1024 * 1024; // 10MB

        fun create(
            apikey: String,
            baseurl: String = "https://api.goingelectric.de",
            context: Context? = null
        ): GoingElectricApi {
            val client = OkHttpClient.Builder().apply {
                addInterceptor { chain ->
                    // add API key to every request
                    var original = chain.request()
                    val url = original.url().newBuilder().addQueryParameter("key", apikey).build()
                    original = original.newBuilder().url(url).build()
                    chain.proceed(original)
                }
                addNetworkInterceptor(StethoInterceptor())
                if (context != null) {
                    cache(Cache(context.getCacheDir(), cacheSize))
                }
            }.build()

            val moshi = Moshi.Builder()
                .add(ChargepointListItemJsonAdapterFactory())
                .add(JsonObjectOrFalseAdapter.Factory())
                .add(HoursAdapter())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseurl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
            return retrofit.create(GoingElectricApi::class.java)
        }
    }
}
