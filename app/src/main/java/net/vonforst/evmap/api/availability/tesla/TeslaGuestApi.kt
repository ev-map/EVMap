package net.vonforst.evmap.api.availability.tesla

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.lang.reflect.Type
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
    suspend fun getChargingSiteDetails(
        @Body request: GetChargingSiteDetailsRequest,
        @Query("operationName") operationName: String = "getGuestChargingSiteDetails"
    ): GetChargingSiteDetailsResponse

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteDetailsRequest(
        override val variables: GetChargingSiteInformationVariables,
        override val operationName: String = "getGuestChargingSiteDetails",
        override val query: String =
            "\n    query getGuestChargingSiteDetails(\$identifier: ChargingSiteIdentifierInput!, \$deviceLocale: String!, \$experience: ChargingExperienceEnum!) {\n  site(\n    identifier: \$identifier\n    deviceLocale: \$deviceLocale\n    experience: \$experience\n  ) {\n    activeOutages\n    address {\n      countryCode\n    }\n    chargers {\n      id\n      label\n    }\n    chargersAvailable {\n      chargerDetails {\n        id\n        availability\n      }\n    }\n    holdAmount {\n      holdAmount\n      currencyCode\n    }\n    maxPowerKw\n    name\n    programType\n    publicStallCount\n    id\n    pricing(experience: \$experience) {\n      userRates {\n        activePricebook {\n          charging {\n            uom\n            rates\n            buckets {\n              start\n              end\n            }\n            bucketUom\n            currencyCode\n            programType\n            vehicleMakeType\n            touRates {\n              enabled\n              activeRatesByTime {\n                startTime\n                endTime\n                rates\n              }\n            }\n          }\n          parking {\n            uom\n            rates\n            buckets {\n              start\n              end\n            }\n            bucketUom\n            currencyCode\n            programType\n            vehicleMakeType\n            touRates {\n              enabled\n              activeRatesByTime {\n                startTime\n                endTime\n                rates\n              }\n            }\n          }\n          congestion {\n            uom\n            rates\n            buckets {\n              start\n              end\n            }\n            bucketUom\n            currencyCode\n            programType\n            vehicleMakeType\n            touRates {\n              enabled\n              activeRatesByTime {\n                startTime\n                endTime\n                rates\n              }\n            }\n          }\n        }\n      }\n    }\n  }\n}\n    "
    ) : GraphQlRequest()

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteInformationVariables(
        val identifier: Identifier,
        val experience: Experience,
        val deviceLocale: String = "de-DE",
    )

    enum class Experience {
        ADHOC, GUEST
    }

    @JsonClass(generateAdapter = true)
    data class Identifier(
        val siteId: ChargingSiteIdentifier
    )

    @JsonClass(generateAdapter = true)
    data class ChargingSiteIdentifier(
        val id: Long,
        val siteType: SiteType = SiteType.SUPERCHARGER
    )

    enum class SiteType {
        @Json(name = "SITE_TYPE_SUPERCHARGER")
        SUPERCHARGER
    }

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteDetailsResponse(val data: GetChargingSiteDetailsResponseData)

    @JsonClass(generateAdapter = true)
    data class GetChargingSiteDetailsResponseData(val site: ChargingSiteInformation?)

    @JsonClass(generateAdapter = true)
    data class ChargingSiteInformation(
        val activeOutages: List<Outage>?,
        val chargers: List<ChargerId>,
        val chargersAvailable: ChargersAvailable,
        val id: Long,
        val maxPowerKw: Int,
        val name: String,
        val pricing: Pricing,
        val publicStallCount: Int
    )

    @JsonClass(generateAdapter = true)
    data class ChargerId(
        val id: String,
        val label: String?,
    ) {
        val labelNumber
            get() = label?.replace(Regex("""\D"""), "")?.toInt()
        val labelLetter
            get() = label?.replace(Regex("""\d"""), "")
    }

    @JsonClass(generateAdapter = true)
    data class ChargersAvailable(val chargerDetails: List<ChargerDetail>)

    @JsonClass(generateAdapter = true)
    data class ChargerDetail(
        val availability: ChargerAvailability,
        val id: String
    )

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