package net.vonforst.evmap.api.chargeprice

import android.content.Context
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import jsonapi.Document
import jsonapi.JsonApiFactory
import jsonapi.retrofit.DocumentConverterFactory
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.model.ChargeLocation
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.*

interface ChargepriceApi {
    @POST("charge_prices")
    suspend fun getChargePrices(
        @Body @jsonapi.retrofit.Document request: ChargepriceRequest,
        @Header("Accept-Language") language: String
    ): Document<List<ChargePrice>>

    @GET("vehicles")
    @jsonapi.retrofit.Document
    suspend fun getVehicles(): List<ChargepriceCar>

    @GET("tariffs")
    @jsonapi.retrofit.Document
    suspend fun getTariffs(): List<ChargepriceTariff>

    @POST("user_feedback")
    suspend fun userFeedback(@Body @jsonapi.retrofit.Document feedback: ChargepriceUserFeedback)

    companion object {
        private val cacheSize = 1L * 1024 * 1024 // 1MB
        val supportedLanguages = setOf("de", "en", "fr", "nl")

        private val DATA_SOURCE_GOINGELECTRIC = "going_electric"
        private val DATA_SOURCE_OPENCHARGEMAP = "open_charge_map"

        private val jsonApiAdapterFactory = JsonApiFactory.Builder()
            .addType(ChargepriceRequest::class.java)
            .addType(ChargepriceTariff::class.java)
            .addType(ChargepriceBrand::class.java)
            .addType(ChargePrice::class.java)
            .addType(ChargepriceCar::class.java)
            .build()
        val moshi = Moshi.Builder()
            .add(jsonApiAdapterFactory)
            .add(
                PolymorphicJsonAdapterFactory.of(ChargepriceUserFeedback::class.java, "type")
                    .withSubtype(ChargepriceMissingPriceFeedback::class.java, "missing_price")
                    .withSubtype(ChargepriceWrongPriceFeedback::class.java, "wrong_price")
                    .withSubtype(ChargepriceMissingVehicleFeedback::class.java, "missing_vehicle")
            )
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
                .addConverterFactory(DocumentConverterFactory.create())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
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

        fun getPoiUrl(charger: ChargeLocation) =
            "https://www.chargeprice.app/?poi_id=${charger.id}&poi_source=${getDataAdapter(charger)}"

        fun getDataAdapter(charger: ChargeLocation) = when (charger.dataSource) {
            "goingelectric" -> DATA_SOURCE_GOINGELECTRIC
            "openchargemap" -> DATA_SOURCE_OPENCHARGEMAP
            else -> throw IllegalArgumentException()
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
