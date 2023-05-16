package net.vonforst.evmap.api.chargeprice

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import jsonapi.Document
import jsonapi.JsonApiFactory
import jsonapi.retrofit.DocumentConverterFactory
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.addDebugInterceptors
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
                    addDebugInterceptors()
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

        /**
         * Checks if a charger is supported by Chargeprice.
         *
         * This function just applies some heuristics on the charger's data without making API
         * calls. If it returns true, that is not a guarantee that Chargeprice will have information
         * on this charger. But if it is false, it is pretty unlikely that Chargeprice will have
         * useful data, so we do not show the price comparison button in this case.
         */
        @JvmStatic
        fun isChargerSupported(charger: ChargeLocation): Boolean {
            val dataSourceSupported = charger.dataSource in listOf("goingelectric", "openchargemap")
            val countrySupported =
                charger.chargepriceData?.country?.let { isCountrySupported(it, charger.dataSource) }
                    ?: false
            val networkSupported = charger.chargepriceData?.network?.let {
                if (charger.dataSource == "openchargemap") {
                    it !in listOf(
                        "1", // unknown operator
                        "44", // private residence/individual
                        "45",  // business owner at location
                        "23", "3534" // Tesla
                    )
                } else if (charger.dataSource == "goingelectric") {
                    it !== "Tesla Supercharger"
                } else {
                    true
                }
            } ?: false
            val powerAvailable = charger.chargepoints.all { it.hasKnownPower() }
            return dataSourceSupported && countrySupported && networkSupported && powerAvailable
        }

        private fun isCountrySupported(country: String, dataSource: String): Boolean =
            when (dataSource) {
                "goingelectric" -> country in listOf(
                    // list of countries according to Chargeprice.app, 2021/08/24
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
                "Irland",
                // additional countries found 2022/09/17, https://github.com/ev-map/EVMap/issues/234
                "Finnland",
                "Lettland",
                "Litauen",
                "Estland",
                "Liechtenstein",
                "Rumänien",
                "Slowakei",
                "Slowenien",
                "Polen",
                "Serbien",
                "Bulgarien",
                "Kosovo",
                "Montenegro",
                "Albanien",
                "Griechenland",
                "Portugal",
                "Island"
            )
            "openchargemap" -> country in listOf(
                // list of countries according to Chargeprice.app, 2021/08/24
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
                "IE",
                // additional countries found 2022/09/17, https://github.com/ev-map/EVMap/issues/234
                "FI",
                "LV",
                "LT",
                "EE",
                "LI",
                "RO",
                "SK",
                "SI",
                "PL",
                "RS",
                "BG",
                "XK",
                "ME",
                "AL",
                "GR",
                "PT",
                "IS"
            )
            else -> false
        }
    }
}
