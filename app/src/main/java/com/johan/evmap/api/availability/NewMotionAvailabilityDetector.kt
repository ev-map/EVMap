package com.johan.evmap.api.availability

import com.johan.evmap.api.distanceBetween
import com.johan.evmap.api.goingelectric.ChargeLocation
import com.johan.evmap.api.goingelectric.Chargepoint
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

private const val coordRange = 0.1  // range of latitude and longitude for loading the map
private const val maxDistance = 15  // max distance between reported positions in meters

interface NewMotionApi {
    @GET("markers/{lngMin}/{lngMax}/{latMin}/{latMax}")
    suspend fun getMarkers(
        @Path("lngMin") lngMin: Double,
        @Path("lngMax") lngMax: Double,
        @Path("latMin") latMin: Double,
        @Path("latMax") latMax: Double
    ): List<NMMarker>

    @GET("locations/{id}")
    suspend fun getLocation(@Path("id") id: Long): NMLocation

    @JsonClass(generateAdapter = true)
    data class NMMarker(val coordinates: NMCoordinates, val locationUid: Long)

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
    data class NMEvse(val evseId: String, val status: String, val connectors: List<NMConnector>)

    @JsonClass(generateAdapter = true)
    data class NMConnector(
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
        fun create(client: OkHttpClient): NewMotionApi {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://my.newmotion.com/api/map/v2/")
                .addConverterFactory(MoshiConverterFactory.create())
                .client(client)
                .build()
            return retrofit.create(NewMotionApi::class.java)
        }
    }
}

class NewMotionAvailabilityDetector(client: OkHttpClient) : BaseAvailabilityDetector(client) {
    val api = NewMotionApi.create(client)

    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val lat = location.coordinates.lat
        val lng = location.coordinates.lng

        // find nearest station to this position
        var markers =
            api.getMarkers(lng - coordRange, lng + coordRange, lat - coordRange, lat + coordRange)
        val nearest = markers.minBy { marker ->
            distanceBetween(marker.coordinates.latitude, marker.coordinates.longitude, lat, lng)
        } ?: throw AvailabilityDetectorException("no candidates found.")

        // combine related stations
        markers = markers.filter { marker ->
            distanceBetween(
                marker.coordinates.latitude,
                marker.coordinates.longitude,
                nearest.coordinates.latitude,
                nearest.coordinates.longitude
            ) < maxDistance
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
                connector to evse.status
            }
        }

        val chargepointStatus = mutableMapOf<Chargepoint, List<ChargepointStatus>>()
        connectorStatus.forEach { (connector, statusStr) ->
            val power = connector.electricalProperties.getPower()
            val type = when (connector.connectorType) {
                "Type2" -> Chargepoint.TYPE_2
                "Domestic" -> Chargepoint.SCHUKO
                "Type2Combo" -> Chargepoint.CCS
                "TepcoCHAdeMO" -> Chargepoint.CHADEMO
                else -> throw IllegalArgumentException("unrecognized type ${connector.connectorType}")
            }
            val status = when (statusStr) {
                "Unavailable" -> ChargepointStatus.FAULTED
                "Available" -> ChargepointStatus.AVAILABLE
                "Occupied" -> ChargepointStatus.CHARGING
                "Unspecified" -> ChargepointStatus.UNKNOWN
                else -> ChargepointStatus.UNKNOWN
            }

            var chargepoint = getCorrespondingChargepoint(chargepointStatus.keys, type, power)
            val statusList: List<ChargepointStatus>
            if (chargepoint == null) {
                // find corresponding chargepoint from goingelectric to get correct power
                val geChargepoint =
                    getCorrespondingChargepoint(location.chargepoints, type, power)
                        ?: throw AvailabilityDetectorException(
                            "Chargepoints from NewMotion API and goingelectric do not match."
                        )
                chargepoint = Chargepoint(
                    type,
                    geChargepoint.power,
                    1
                )
                statusList = listOf(status)
            } else {
                val previousStatus = chargepointStatus[chargepoint]!!
                statusList = previousStatus + listOf(status)
                chargepointStatus.remove(chargepoint)
                chargepoint =
                    Chargepoint(
                        chargepoint.type,
                        chargepoint.power,
                        chargepoint.count + 1
                    )
            }

            chargepointStatus[chargepoint] = statusList
        }

        if (chargepointStatus.keys == location.chargepoints.toSet()) {
            return ChargeLocationStatus(
                chargepointStatus,
                "NewMotion"
            )
        } else {
            throw AvailabilityDetectorException(
                "Chargepoints from NewMotion API and goingelectric do not match."
            )
        }
    }

}