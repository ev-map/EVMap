package net.vonforst.evmap.api.fronyx

import android.content.Context
import com.squareup.moshi.Moshi
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.addDebugInterceptors
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

private interface FronyxApiRetrofit {
    @GET("predictions/evse-id/{evseId}")
    suspend fun getPredictionsForEvseId(
        @Path("evseId") evseId: String,
        @Query("timeframe") timeframe: Int? = null
    ): FronyxEvseIdResponse

    @GET("predictions/evses")
    suspend fun getPredictionsForEvseIds(
        @Query("evseIds", encoded = true) evseIds: String  // comma-separated
    ): List<FronyxEvseIdResponse>

    companion object {
        private val cacheSize = 1L * 1024 * 1024 // 1MB

        private val moshi = Moshi.Builder()
            .add(ZonedDateTimeAdapter())
            .build()

        fun create(
            apikey: String,
            baseurl: String = "https://api.fronyx.io/api/",
            context: Context? = null
        ): FronyxApiRetrofit {
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
                    addDebugInterceptors()
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
            return retrofit.create(FronyxApiRetrofit::class.java)
        }
    }
}

class FronyxApi(
    apikey: String,
    baseurl: String = "https://api.fronyx.io/api/",
    context: Context? = null
) {
    private val api = FronyxApiRetrofit.create(apikey, baseurl, context)

    suspend fun getPredictionsForEvseId(
        evseId: String,
        timeframe: Int? = null
    ): FronyxEvseIdResponse = api.getPredictionsForEvseId(evseId, timeframe)

    suspend fun getPredictionsForEvseIds(
        evseIds: List<String>
    ): List<FronyxEvseIdResponse> = api.getPredictionsForEvseIds(evseIds.joinToString(","))

    companion object {
        /**
         * Checks if a chargepoint is supported by Fronyx.
         *
         * This function just applies some heuristics on the charger's data without making API
         * calls. If it returns true, that is not a guarantee that Fronyx will have information
         * on this chargepoint. But if it is false, it is pretty unlikely that Fronyx will have
         * useful data, so we do not try to load the data in this case.
         */
        fun isChargepointSupported(charger: ChargeLocation, chargepoint: Chargepoint): Boolean {
            if (charger.address?.country !in listOf("Deutschland", "Germany")) {
                // fronyx only predicts for chargers in Germany for now
                return false
            }
            if (chargepoint.type !in listOf(
                    Chargepoint.CCS_UNKNOWN,
                    Chargepoint.CCS_TYPE_2,
                    Chargepoint.CHADEMO
                )
            ) {
                // fronyx only predicts DC chargers for now
                return false
            }
            return true
        }
    }
}