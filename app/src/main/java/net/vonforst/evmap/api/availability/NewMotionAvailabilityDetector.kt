package net.vonforst.evmap.api.availability

import com.squareup.moshi.JsonClass
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.utils.distanceBetween
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.*

private const val coordRange = 0.005  // range of latitude and longitude for loading the map
private const val maxDistance = 60  // max distance between reported positions in meters

interface NewMotionApi {
    @GET("markers/{lngMin}/{lngMax}/{latMin}/{latMax}/{zoom}")
    suspend fun getMarkers(
        @Path("lngMin") lngMin: Double,
        @Path("lngMax") lngMax: Double,
        @Path("latMin") latMin: Double,
        @Path("latMax") latMax: Double,
        @Path("zoom") zoom: Int = 22
    ): List<NMMarker>

    @GET("locations/{id}")
    suspend fun getLocation(@Path("id") id: Long): NMLocation

    @JsonClass(generateAdapter = true)
    data class NMMarker(val coordinates: NMCoordinates, val locationUid: Long, val evseCount: Int)

    @JsonClass(generateAdapter = true)
    data class NMCoordinates(val latitude: Double, val longitude: Double)

    @JsonClass(generateAdapter = true)
    data class NMLocation(
        val uid: Long,
        val coordinates: NMCoordinates,
        val operatorName: String,
        val evses: List<NMEvse>
    )

    @JsonClass(generateAdapter = true)
    data class NMEvse(val evseId: String?, val status: String, val connectors: List<NMConnector>)

    @JsonClass(generateAdapter = true)
    data class NMConnector(
        val uid: Long,
        val connectorType: String,
        val electricalProperties: NMElectricalProperties
    )

    @JsonClass(generateAdapter = true)
    data class NMElectricalProperties(val powerType: String, val voltage: Int, val amperage: Int) {
        fun getPower(): Double {
            val phases = when (powerType) {
                "AC1Phase" -> 1
                "AC3Phase" -> 3
                else -> 1
            }
            val volt = when (voltage) {
                277 -> 230
                else -> voltage
            }
            val power = volt * amperage * phases
            return when (power) {
                3680 -> 3.7
                11040 -> 11.0
                22080 -> 22.0
                43470 -> 43.0
                else -> power / 1000.0
            }
        }
    }

    companion object {
        fun create(client: OkHttpClient, baseUrl: String? = null): NewMotionApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl ?: "https://ui-map.shellrecharge.com/api/map/v2/")
                .addConverterFactory(MoshiConverterFactory.create())
                .client(client)
                .build()
            return retrofit.create(NewMotionApi::class.java)
        }
    }
}

class NewMotionAvailabilityDetector(client: OkHttpClient, baseUrl: String? = null) :
    BaseAvailabilityDetector(client) {
    val api = NewMotionApi.create(client, baseUrl)

    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val lat = location.coordinates.lat
        val lng = location.coordinates.lng

        // find nearest station to this position
        var markers =
            api.getMarkers(lng - coordRange, lng + coordRange, lat - coordRange, lat + coordRange)
        val nearest = markers.minByOrNull { marker ->
            distanceBetween(marker.coordinates.latitude, marker.coordinates.longitude, lat, lng)
        } ?: throw AvailabilityDetectorException("no candidates found.")

        if (distanceBetween(
                nearest.coordinates.latitude,
                nearest.coordinates.longitude,
                lat,
                lng
            ) > radius
        ) {
            throw AvailabilityDetectorException("no candidates found")
        }

        if (nearest.evseCount < location.totalChargepoints) {
            // combine related stations
            markers = markers.filter { marker ->
                distanceBetween(
                    marker.coordinates.latitude,
                    marker.coordinates.longitude,
                    nearest.coordinates.latitude,
                    nearest.coordinates.longitude
                ) < maxDistance
            }
        } else {
            markers = listOf(nearest)
        }

        // load details
        var details = markers.map {
            api.getLocation(it.locationUid)
        }
        // only include stations from same operator
        details = details.filter {
            it.operatorName == details[0].operatorName
        }
        val connectorStatus = details.flatMap { it.evses }.flatMap { evse ->
            evse.connectors.map { connector ->
                Triple(connector, evse.status, evse.evseId)
            }
        }

        val nmConnectors = mutableMapOf<Long, Pair<Double, String>>()
        val nmStatus = mutableMapOf<Long, ChargepointStatus>()
        val nmEvseId = mutableMapOf<Long, String>()
        connectorStatus.forEach { (connector, statusStr, evseId) ->
            val id = connector.uid
            val power = connector.electricalProperties.getPower()
            val type = when (connector.connectorType.lowercase(Locale.ROOT)) {
                "type3" -> Chargepoint.TYPE_3
                "type2" -> Chargepoint.TYPE_2_UNKNOWN
                "type1" -> Chargepoint.TYPE_1
                "domestic" -> Chargepoint.SCHUKO
                "type1combo" -> Chargepoint.CCS_TYPE_1  // US CCS, aka type1_combo
                "type2combo" -> Chargepoint.CCS_TYPE_2  // EU CCS, aka type2_combo
                "tepcochademo" -> Chargepoint.CHADEMO
                "unspecified" -> "unknown"
                "unknown" -> "unknown"
                "saej1772" -> "unknown"
                else -> "unknown"
            }
            val status = when (statusStr) {
                "Unavailable" -> ChargepointStatus.FAULTED
                "Available" -> ChargepointStatus.AVAILABLE
                "Occupied" -> ChargepointStatus.CHARGING
                "Unspecified" -> ChargepointStatus.UNKNOWN
                else -> ChargepointStatus.UNKNOWN
            }
            nmConnectors.put(id, power to type)
            nmStatus.put(id, status)
            evseId?.let { nmEvseId[id] = it }
        }

        val match = matchChargepoints(nmConnectors, location.chargepointsMerged)
        val chargepointStatus = match.mapValues { entry ->
            entry.value.map { nmStatus[it]!! }
        }
        val evseIds = if (nmEvseId.size == nmStatus.size) match.mapValues { entry ->
            entry.value.map { nmEvseId[it]!! }
        } else null
        return ChargeLocationStatus(
            chargepointStatus,
            "NewMotion",
            evseIds
        )
    }

    override fun isChargerSupported(charger: ChargeLocation): Boolean {
        // NewMotion is our fallback
        return when (charger.dataSource) {
            "goingelectric" -> charger.network != "Tesla Supercharger"
            "openchargemap" -> charger.chargepriceData?.network !in listOf("23", "3534")
            "openstreetmap" -> charger.operator !in listOf("Tesla, Inc.", "Tesla")
            else -> false
        }
    }

}