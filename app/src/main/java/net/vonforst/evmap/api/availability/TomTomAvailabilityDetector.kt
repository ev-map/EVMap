package net.vonforst.evmap.api.availability

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import net.vonforst.evmap.R
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface TomTomApi {
    @GET("poiSearch/ev charging station.json")
    suspend fun getChargers(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int,
        @Query("key") key: String,
        //@Query("minPowerKW") minPowerKW: Double,
        //@Query("maxPowerKW") maxPowerKW: Double,
        // Can't get this to work:
        //@Query("categorySet") categorySet: String = "7309" // EV charging station
    ): TomTomPoiSearchResponse

    @GET("chargingAvailability.json")
    suspend fun getAvailability(
        @Query("chargingAvailability") chargingAvailability: String,
        @Query("key") key: String
    ): TomTomAvailabilityResponse

    @JsonClass(generateAdapter = true)
    data class TomTomPoiSearchResponse(
        @Json(name = "summary") val summary: TomTomPoiSearchSummary,
        @Json(name = "results") val results: List<TomTomPoiSearchResult>
    )

    @JsonClass(generateAdapter = true)
    data class TomTomPoiSearchSummary(
        @Json(name = "query") val query: String,
        @Json(name = "queryType") val queryType: String,
        @Json(name = "queryTime") val queryTime: Int,
        @Json(name = "numResults") val numResults: Int,
        @Json(name = "offset") val offset: Int,
        @Json(name = "totalResults") val totalResults: Int,
        @Json(name = "fuzzyLevel") val fuzzyLevel: Int,
        @Json(name = "geoBias") val geoBias: Map<String, Double>
    )

    @JsonClass(generateAdapter = true)
    data class TomTomPoiSearchResult(
        @Json(name = "type") val type: String,
        @Json(name = "id") val id: String,
        @Json(name = "score") val score: Double,
        @Json(name = "dist") val dist: Double,
        @Json(name = "info") val info: String,
        @Json(name = "matchConfidence") val matchConfidence: Map<String, Double>,
        @Json(name = "poi") val poi: Map<String, Any>,
        @Json(name = "address") val address: Map<String, String>,
        @Json(name = "position") val position: Map<String, Double>,
        @Json(name = "viewport") val viewport: Map<String, Map<String, Double>>,
        @Json(name = "vehicleTypes") val vehicleTypes: List<String>,
        @Json(name = "chargingPark") val chargingPark: Map<String, List<Map<String, Any>>>,
        @Json(name = "dataSources") val dataSources: Map<String, Map<String, String>>?
    )

    @JsonClass(generateAdapter = true)
    data class TomTomAvailabilityResponse(
        @Json(name = "connectors") val connectors: List<TomTomAvailabilityConnector>,
        @Json(name = "chargingAvailability") val chargingAvailability: String
    )

    @JsonClass(generateAdapter = true)
    data class TomTomAvailabilityConnector(
        @Json(name = "type") val type: String,
        @Json(name = "total") val total: Int,
        @Json(name = "availability") val availability: TomTomConnectorAvailability
    )

    @JsonClass(generateAdapter = true)
    data class TomTomConnectorAvailability(
        @Json(name = "current") val current: Map<String, Int>,
        @Json(name = "perPowerLevel") val perPowerLevel: List<TomTomConnectorPowerAvailability>
    )

    @JsonClass(generateAdapter = true)
    data class TomTomConnectorPowerAvailability(
        @Json(name = "powerKW") val powerKw: Double,
        @Json(name = "available") val available: Int,
        @Json(name = "occupied") val occupied: Int,
        @Json(name = "reserved") val reserved: Int,
        @Json(name = "unknown") val unknown: Int,
        @Json(name = "outOfService") val outOfService: Int
    )

    companion object {
        fun create(client: OkHttpClient): TomTomApi {
            val clientWithInterceptor = client.newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder().build()
                    chain.proceed(request)
                }.build()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.tomtom.com/search/2/")
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
                .client(clientWithInterceptor)
                .build()
            return retrofit.create(TomTomApi::class.java)
        }
    }
}

class TomTomAvailabilityDetector(client: OkHttpClient, context: Context) : BaseAvailabilityDetector(client) {
    val tomTomKey = context.getString(R.string.tomtom_key)
    val api = TomTomApi.create(client)

    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val chargers = api.getChargers(location.coordinates.lat, location.coordinates.lng, radius, tomTomKey)
        if (chargers.results.isEmpty()) {
            throw AvailabilityDetectorException("no candidates found.")
        }

        val bestMatchResult = chargers.results.find { result ->
            result.chargingPark.getValue("connectors").size == location.totalChargepoints }
        val availabilityId = bestMatchResult?.dataSources?.getValue("chargingAvailability")?.getValue("id")
        if (availabilityId == null) {
            throw AvailabilityDetectorException("no candidates found.")
        }
        val availability = api.getAvailability(availabilityId, tomTomKey)

        var index = 0L
        val tomTomConnectors = mutableMapOf<Long, Pair<Double, String>>()
        val tomTomStatus = mutableMapOf<Long, ChargepointStatus>()
        availability.connectors.forEach { connector ->
            val type = when (connector.type) {
                "StandardHouseholdCountrySpecific" -> Chargepoint.SCHUKO
                "IEC62196Type1" -> Chargepoint.TYPE_1
                "IEC62196Type1CCS" -> Chargepoint.CCS_TYPE_1
                "IEC62196Type2CableAttached" -> Chargepoint.TYPE_2_PLUG
                "IEC62196Type2Outlet" -> Chargepoint.TYPE_2_SOCKET
                "IEC62196Type2CCS" -> Chargepoint.CCS_TYPE_2
                "IEC62196Type3" -> Chargepoint.TYPE_3A  // Actually either 3A or 3C
                "Chademo" -> Chargepoint.CHADEMO
                "IEC60309AC3PhaseRed" -> Chargepoint.CEE_ROT
                "IEC60309AC1PhaseBlue" -> Chargepoint.CEE_BLAU
                "Tesla" -> Chargepoint.SUPERCHARGER
                else -> "unknown"
            }
            connector.availability.perPowerLevel.forEach {
                val power = it.powerKw
                for (i in 1..(it.available + it.occupied + it.reserved + it.unknown + it.outOfService)) {
                    tomTomConnectors.put(index, power to type)
                    val status = when {
                        i <= it.available -> ChargepointStatus.AVAILABLE
                        i <= it.available + it.occupied -> ChargepointStatus.OCCUPIED
                        i <= it.available + it.occupied + it.reserved -> ChargepointStatus.OCCUPIED
                        i <= it.available + it.occupied + it.reserved + it.unknown -> ChargepointStatus.UNKNOWN
                        i <= it.available + it.occupied + it.reserved + it.unknown + it.outOfService -> ChargepointStatus.FAULTED
                        else -> ChargepointStatus.UNKNOWN
                    }
                    tomTomStatus.put(index, status)
                    index += 1
                }
            }
        }
        val match = matchChargepoints(tomTomConnectors, location.chargepointsMerged)
        val chargepointStatus = match.mapValues { entry ->
            entry.value.map { tomTomStatus[it]!! }
        }
        return ChargeLocationStatus(
            chargepointStatus,
            "TomTom"
        )
    }

    override fun isChargerSupported(charger: ChargeLocation): Boolean {
        return when (charger.dataSource) {
            "nobil" -> charger.network != "Tesla"
            else -> false
        }
    }
}