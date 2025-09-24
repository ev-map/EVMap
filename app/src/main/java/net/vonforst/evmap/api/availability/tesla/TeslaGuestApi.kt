package net.vonforst.evmap.api.availability.tesla

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TeslaCuaApi {
    @GET("tesla-locations")
    suspend fun getTeslaLocations(
        @Query("translate") translate: String = "en_US",
        @Query("usetrt") usetrt: Boolean = true,
    ): List<TeslaLocation>

    @GET("tesla-location")
    suspend fun getTeslaLocation(
        @Query("id") id: String,
        @Query("translate") translate: String = "en_US",
        @Query("usetrt") usetrt: Boolean = true
    ): TeslaLocation

    @JsonClass(generateAdapter = true)
    data class TeslaLocation(
        val latitude: Double?,
        val longitude: Double?,
        @Json(name = "location_id") val locationId: String,
        val title: String?,
        @Json(name = "location_type") val locationType: List<String>,
        val trtId: String?
    )

    companion object {
        fun create(
            client: OkHttpClient,
            baseUrl: String? = null
        ): TeslaCuaApi {
            val clientWithInterceptor = client.newBuilder()
                .addInterceptor { chain ->
                    // increase cache duration to 24h (useful for the large getTeslaLocations request)
                    val request = chain.request().newBuilder()
                        .cacheControl(CacheControl.Builder().maxStale(24, TimeUnit.HOURS).build())
                        .build()
                    chain.proceed(request)
                }.build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl ?: "https://www.tesla.com/cua-api/")
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder().add(LocalTimeAdapter()).build()
                    )
                )
                .client(clientWithInterceptor)
                .build()
            return retrofit.create(TeslaCuaApi::class.java)
        }
    }
}

interface TeslaChargingGuestGraphQlApi {
    @POST("graphql")
    suspend fun getSiteDetails(
        @Body request: GetSiteDetailsRequest,
        @Query("operationName") operationName: String = "GetSiteDetails"
    ): GetChargingSiteDetailsResponse

    @JsonClass(generateAdapter = true)
    data class GetSiteDetailsRequest(
        override val variables: GetSiteDetailsVariables,
        override val operationName: String = "GetSiteDetails",
        override val query: String =
            "\n    query GetSiteDetails(\$siteId: SiteIdInput!) {\n  chargingNetwork {\n    site(siteId: \$siteId) {\n      address {\n        countryCode\n      }\n      chargerList {\n        id\n        label\n        availability\n      }\n      holdAmount {\n        amount\n        currencyCode\n      }\n      maxPowerKw\n      name\n      programType\n      publicStallCount\n      trtId\n      pricing {\n        userRates {\n          activePricebook {\n            charging {\n              ...ChargingRate\n            }\n            parking {\n              ...ChargingRate\n            }\n            congestion {\n              ...ChargingRate\n            }\n          }\n        }\n      }\n    }\n  }\n}\n    \n    fragment ChargingRate on ChargingUserRate {\n  uom\n  rates\n  buckets {\n    start\n    end\n  }\n  bucketUom\n  currencyCode\n  programType\n  vehicleMakeType\n  touRates {\n    enabled\n    activeRatesByTime {\n      startTime\n      endTime\n      rates\n    }\n  }\n}\n    "
    ) : GraphQlRequest()

    @JsonClass(generateAdapter = true)
    data class GetSiteDetailsVariables(
        val siteId: Identifier,
    )

    enum class Experience {
        ADHOC, GUEST
    }

    @JsonClass(generateAdapter = true)
    data class Identifier(
        val byTrtId: ChargingSiteIdentifier
    )

    @JsonClass(generateAdapter = true)
    data class ChargingSiteIdentifier(
        val trtId: Long,
        val chargingExperience: Experience,
        val programType: String = "PTSCH",
        val locale: String = "de-DE",
    )

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteDetailsResponse(val data: GetChargingSiteDetailsResponseDataNetwork)

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteDetailsResponseDataNetwork(val chargingNetwork: GetChargingSiteDetailsResponseData?)

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteDetailsResponseData(val site: ChargingSiteInformation?)

    @JsonClass(generateAdapter = true)
    data class ChargingSiteInformation(
        val activeOutages: List<Outage>?,
        val chargerList: List<ChargerDetail>,
        val trtId: Long,
        val maxPowerKw: Int?,
        val name: String,
        val pricing: Pricing?,
        val publicStallCount: Int
    )

    @JsonClass(generateAdapter = true)
    data class ChargerDetail(
        val availability: ChargerAvailability,
        val label: String?,
        val id: String
    ) {
        val labelNumber
            get() = label?.replace(Regex("""\D"""), "")?.toInt()
        val labelLetter
            get() = label?.replace(Regex("""\d"""), "")
    }

    companion object {
        fun create(
            client: OkHttpClient,
            baseUrl: String? = null
        ): TeslaChargingGuestGraphQlApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl ?: "https://www.tesla.com/de_DE/charging/guest/api/")
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder().add(LocalTimeAdapter()).build()
                    )
                )
                .client(client)
                .build()
            return retrofit.create(TeslaChargingGuestGraphQlApi::class.java)
        }
    }
}