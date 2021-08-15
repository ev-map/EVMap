package net.vonforst.evmap.api.chargeprice

import android.content.Context
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import moe.banana.jsonapi2.ArrayDocument
import moe.banana.jsonapi2.JsonApiConverterFactory
import moe.banana.jsonapi2.ResourceAdapterFactory
import net.vonforst.evmap.BuildConfig
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.*

interface ChargepriceApi {
    @POST("charge_prices")
    suspend fun getChargePrices(
        @Body request: ChargepriceRequest,
        @Header("Accept-Language") language: String
    ): ArrayDocument<ChargePrice>

    @GET("vehicles")
    suspend fun getVehicles(): ArrayDocument<ChargepriceCar>

    @GET("tariffs")
    suspend fun getTariffs(): ArrayDocument<ChargepriceTariff>

    companion object {
        private val cacheSize = 1L * 1024 * 1024 // 1MB
        val supportedLanguages = setOf("de", "en", "fr", "nl")

        val DATA_SOURCE_GOINGELECTRIC = "going_electric"
        val DATA_SOURCE_OPENCHARGEMAP = "open_charge_map"

        private val jsonApiAdapterFactory = ResourceAdapterFactory.builder()
            .add(ChargepriceRequest::class.java)
            .add(ChargepriceTariff::class.java)
            .add(ChargepriceBrand::class.java)
            .add(ChargePrice::class.java)
            .add(ChargepriceCar::class.java)
            .build()
        val moshi = Moshi.Builder()
            .add(jsonApiAdapterFactory)
            .add(KotlinJsonAdapterFactory())
            .build()
        fun create(
            apikey: String,
            baseurl: String = "https://api.chargeprice.app/v1/",
            context: Context? = null
        ): ChargepriceApi {
            val client = OkHttpClient.Builder().apply {
                addInterceptor { chain ->
                    // add API key to every request
                    val original = chain.request()
                    val new = original.newBuilder()
                        .header("API-Key", apikey)
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
                .addConverterFactory(JsonApiConverterFactory.create(moshi))
                .client(client)
                .build()
            return retrofit.create(ChargepriceApi::class.java)
        }


        fun getChargepriceLanguage(): String {
            val locale = Locale.getDefault().language
            return if (supportedLanguages.contains(locale)) {
                locale
            } else {
                "en"
            }
        }

        @JvmStatic
        fun isCountrySupported(country: String, dataSource: String): Boolean = when (dataSource) {
            // list of countries updated 2021/08/24
            "goingelectric" -> country in listOf(
                "Deutschland",
                "Österreich",
                "Schweiz",
                "Frankreich",
                "Belgien",
                "Niederlande",
                "Luxemburg",
                "Dänemark",
                "Norwegen",
                "Schweden",
                "Slowenien",
                "Kroatien",
                "Ungarn",
                "Tschechien",
                "Italien",
                "Spanien",
                "Großbritannien",
                "Irland"
            )
            "openchargemap" -> country in listOf(
                "DE",
                "AT",
                "CH",
                "FR",
                "BE",
                "NE",
                "LU",
                "DK",
                "NO",
                "SE",
                "SI",
                "HR",
                "HU",
                "CZ",
                "IT",
                "ES",
                "GB",
                "IE"
            )
            else -> false
        }
    }
}
