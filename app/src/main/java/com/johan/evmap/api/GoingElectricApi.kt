package com.johan.evmap.api

import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface GoingElectricApi {
    @GET("/chargepoints/")
    fun getChargepoints(
        @Query("sw_lat") swlat: Double, @Query("sw_lng") sw_lng: Double,
        @Query("ne_lat") ne_lat: Double, @Query("ne_lng") ne_lng: Double,
        @Query("clustering") clustering: Boolean,
        @Query("zoom") zoom: Float,
        @Query("cluster_distance") clusterDistance: Int
    ): Call<ChargepointList>

    @GET("/chargepoints/")
    fun getChargepointDetail(@Query("ge_id") id: Long): Call<ChargepointList>

    companion object {
        fun create(apikey: String): GoingElectricApi {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    // add API key to every request
                    var original = chain.request()
                    val url = original.url().newBuilder().addQueryParameter("key", apikey).build()
                    original = original.newBuilder().url(url).build()
                    chain.proceed(original)
                }
                .addNetworkInterceptor(StethoInterceptor())
                .build()

            val moshi = Moshi.Builder()
                .add(ChargepointListItemJsonAdapterFactory())
                .add(JsonObjectOrFalseAdapter.Factory())
                .add(HoursAdapter())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.goingelectric.de")
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
            return retrofit.create(GoingElectricApi::class.java)
        }
    }
}
