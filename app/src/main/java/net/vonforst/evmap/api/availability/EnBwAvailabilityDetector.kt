package net.vonforst.evmap.api.availability

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.utils.distanceBetween
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant

private const val coordRange = 0.005  // range of latitude and longitude for loading the map
private const val maxDistance = 60  // max distance between reported positions in meters

interface EnBwApi {
    @GET("chargestations?grouping=false")
    suspend fun getMarkers(
        @Query("fromLon") fromLon: Double,
        @Query("toLon") toLon: Double,
        @Query("fromLat") fromLat: Double,
        @Query("toLat") toLat: Double,
    ): List<EnBwLocation>

    @GET("chargestations/{id}")
    suspend fun getLocation(@Path("id") id: Long): EnBwLocationDetail

    @JsonClass(generateAdapter = true)
    data class EnBwLocation(
        val lat: Double,
        val lon: Double,
        val stationId: Long?,
        val grouped: Boolean,
        val availableChargePoints: Int,
        val numberOfChargePoints: Int,
        val operator: String,
        val viewPort: EnBwViewport
    )

    @JsonClass(generateAdapter = true)
    data class EnBwLocationDetail(
        val lat: Double,
        val lon: Double,
        val stationId: Long,
        val availableChargePoints: Int,
        val numberOfChargePoints: Int,
        val operator: String,
        val chargePoints: List<EnBwChargePoint>
    )

    @JsonClass(generateAdapter = true)
    data class EnBwChargePoint(
        val evseId: String?,
        val status: String,
        val connectors: List<EnBwConnector>,
        val state: EnBwState?
    )

    @JsonClass(generateAdapter = true)
    data class EnBwConnector(
        val plugTypeName: String,
        val maxPowerInKw: Double?,
    )

    @JsonClass(generateAdapter = true)
    data class EnBwViewport(
        val lowerLeftLat: Double,
        val lowerLeftLon: Double,
        val upperRightLat: Double,
        val upperRightLon: Double
    )

    @JsonClass(generateAdapter = true)
    data class EnBwState(
        val updatedAt: Instant?
    )

    companion object {
        fun create(client: OkHttpClient, baseUrl: String? = null): EnBwApi {
            val clientWithInterceptor = client.newBuilder()
                .addInterceptor { chain ->
                    // add API key to every request
                    val request = chain.request().newBuilder()
                        .header("Ocp-Apim-Subscription-Key", "d4954e8b2e444fc89a89a463788c0a72")
                        .header("Origin", "https://www.enbw.com")
                        .header("Referer", "https://www.enbw.com/")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }.build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl ?: "https://enbw-emp.azure-api.net/emobility-public-api/api/v1/")
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder().add(InstantAdapter()).build()
                    )
                )
                .client(clientWithInterceptor)
                .build()
            return retrofit.create(EnBwApi::class.java)
        }
    }
}

internal class InstantAdapter {
    @FromJson
    fun fromJson(value: Long?): Instant? = value?.let {
        Instant.ofEpochMilli(it)
    }

    @ToJson
    fun toJson(value: Instant?): Long? = value?.toEpochMilli()
}

data class EnBwStatus(
    val conn: EnBwApi.EnBwConnector,
    val status: String,
    val evseId: String?,
    val lastChange: Instant?
)

class EnBwAvailabilityDetector(client: OkHttpClient, baseUrl: String? = null) :
    BaseAvailabilityDetector(client) {
    val api = EnBwApi.create(client, baseUrl)

    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val lat = location.coordinates.lat
        val lng = location.coordinates.lng

        // find nearest station to this position
        var markers =
            api.getMarkers(lng - coordRange, lng + coordRange, lat - coordRange, lat + coordRange)

        markers = markers.flatMap {
            if (it.grouped) {
                api.getMarkers(
                    it.viewPort.lowerLeftLon,
                    it.viewPort.upperRightLon,
                    it.viewPort.lowerLeftLat,
                    it.viewPort.upperRightLat
                )
            } else {
                listOf(it)
            }
        }
        if (markers.any { it.grouped }) throw AvailabilityDetectorException("markers still grouped")

        val nearest = markers.minByOrNull { marker ->
            distanceBetween(marker.lat, marker.lon, lat, lng)
        } ?: throw AvailabilityDetectorException("no candidates found.")

        if (distanceBetween(
                nearest.lat,
                nearest.lon,
                lat,
                lng
            ) > radius
        ) {
            throw AvailabilityDetectorException("no candidates found")
        }

        if (nearest.numberOfChargePoints < location.totalChargepoints) {
            // combine related stations
            markers = markers.filter { marker ->
                distanceBetween(
                    marker.lat,
                    marker.lon,
                    nearest.lat,
                    nearest.lon
                ) < maxDistance
            }.filter {
                // only include stations from same operator
                it.operator == nearest.operator && it.stationId != null
            }
        } else {
            markers = listOf(nearest)
        }

        val details = markers.mapNotNull { it.stationId }.map {
            // load details
            api.getLocation(it)
        }

        val connectorStatus = details.flatMap { it.chargePoints }.flatMap { cp ->
            cp.connectors.map { connector ->
                EnBwStatus(connector, cp.status, cp.evseId, cp.state?.updatedAt)
            }
        }

        val enbwConnectors = mutableMapOf<Long, Pair<Double, String>>()
        val enbwStatus = mutableMapOf<Long, ChargepointStatus>()
        val enbwEvseId = mutableMapOf<Long, String>()
        val enbwLastChange = mutableMapOf<Long, Instant?>()
        connectorStatus.forEachIndexed { index, (connector, statusStr, evseId, updatedAt) ->
            val id = index.toLong()
            val power = connector.maxPowerInKw ?: 0.0
            val type = when (connector.plugTypeName) {
                "Typ 3A" -> Chargepoint.TYPE_3A
                "Typ 3C \"Scame\"" -> Chargepoint.TYPE_3C
                "Typ 2" -> Chargepoint.TYPE_2_UNKNOWN
                "Typ 1" -> Chargepoint.TYPE_1
                "Steckdose(D)" -> Chargepoint.SCHUKO
                "CCS (Typ 1)" -> Chargepoint.CCS_TYPE_1  // US CCS, aka type1_combo
                "CCS (Typ 2)" -> Chargepoint.CCS_TYPE_2  // EU CCS, aka type2_combo
                "CHAdeMO" -> Chargepoint.CHADEMO
                else -> "unknown"
            }
            val status = when (statusStr) {
                "UNAVAILABLE" -> ChargepointStatus.FAULTED
                "OUT_OF_SERVICE" -> ChargepointStatus.FAULTED
                "AVAILABLE" -> ChargepointStatus.AVAILABLE
                "OCCUPIED" -> ChargepointStatus.CHARGING
                "UNSPECIFIED" -> ChargepointStatus.UNKNOWN
                else -> ChargepointStatus.UNKNOWN
            }
            enbwConnectors[id] = power to type
            enbwStatus[id] = status
            enbwLastChange[id] = updatedAt
            evseId?.let { enbwEvseId[id] = it }
        }

        val match = matchChargepoints(enbwConnectors, location.chargepointsMerged)
        val chargepointStatus = match.mapValues { entry ->
            entry.value.map { enbwStatus[it]!! }
        }
        val evseIds = if (enbwEvseId.size == enbwStatus.size) match.mapValues { entry ->
            entry.value.map { enbwEvseId[it]!! }
        } else null
        val lastChange =
            if (enbwLastChange.size == enbwStatus.size) match.mapValues { entry -> entry.value.map { enbwLastChange[it] } } else null
        return ChargeLocationStatus(
            chargepointStatus,
            "EnBW",
            evseIds,
            lastChange = lastChange
        )
    }

    override fun isChargerSupported(charger: ChargeLocation): Boolean {
        val country = charger.chargepriceData?.country ?: charger.address?.country

        return when (charger.dataSource) {
            // list of countries as of 2023/04/14, according to
            // https://www.enbw.com/elektromobilitaet/produkte/ladetarife
            "goingelectric" -> country in listOf(
                "Deutschland",
                "Österreich",
                "Schweiz",
                "Belgien",
                "Dänemark",
                "Frankreich",
                "Italien",
                "Kroatien",
                "Liechtenstein",
                "Luxemburg",
                "Niederlande",
                "Polen",
                "Schweden",
                "Slowakei",
                "Slowenien",
                "Spanien",
                "Tschechien"
            ) && charger.network != "Tesla Supercharger"
            "openchargemap" -> country in listOf(
                "DE",
                "AT",
                "CH",
                "BE",
                "DK",
                "FR",
                "IT",
                "HR",
                "LI",
                "LU",
                "NE",
                "PL",
                "SE",
                "SK",
                "SI",
                "ES",
                "CZ"
            ) && charger.chargepriceData?.network !in listOf("23", "3534")
            /* TODO: OSM usually does not have the country tagged. Therefore we currently just use
               a bounding box to determine whether the charger is roughly in Europe */
            "openstreetmap" -> charger.coordinates.lat in 35.0..72.0
                    && charger.coordinates.lng in 25.0..65.0
                    && charger.operator !in listOf("Tesla, Inc.", "Tesla")

            else -> false
        }
    }
}