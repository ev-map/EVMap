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
import retrofit2.http.Query

private const val coordRange = 0.005  // range of latitude and longitude for loading the map
private const val maxDistance = 40  // max distance between reported positions in meters

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
        val connectors: List<EnBwConnector>
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
                .addConverterFactory(MoshiConverterFactory.create())
                .client(clientWithInterceptor)
                .build()
            return retrofit.create(EnBwApi::class.java)
        }
    }
}

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

        // combine related stations
        markers = markers.filter { marker ->
            distanceBetween(
                marker.lat,
                marker.lon,
                nearest.lat,
                nearest.lon
            ) < maxDistance
        }

        var details = markers.filter {
            // only include stations from same operator
            it.operator == nearest.operator && it.stationId != null
        }.map {
            // load details
            api.getLocation(it.stationId!!)
        }

        val connectorStatus = details.flatMap { it.chargePoints }.flatMap { cp ->
            cp.connectors.map { connector ->
                Triple(connector, cp.status, cp.evseId)
            }
        }

        val enbwConnectors = mutableMapOf<Long, Pair<Double, String>>()
        val enbwStatus = mutableMapOf<Long, ChargepointStatus>()
        val enbwEvseId = mutableMapOf<Long, String>()
        connectorStatus.forEachIndexed { index, (connector, statusStr, evseId) ->
            val id = index.toLong()
            val power = connector.maxPowerInKw ?: 0.0
            val type = when (connector.plugTypeName) {
                "Typ 3A" -> Chargepoint.TYPE_3
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
            evseId?.let { enbwEvseId[id] = it }
        }

        val match = matchChargepoints(enbwConnectors, location.chargepointsMerged)
        val chargepointStatus = match.mapValues { entry ->
            entry.value.map { enbwStatus[it]!! }
        }
        val evseIds = if (enbwEvseId.size == enbwStatus.size) match.mapValues { entry ->
            entry.value.map { enbwEvseId[it]!! }
        } else null
        return ChargeLocationStatus(
            chargepointStatus,
            "EnBW",
            evseIds
        )
    }

    override fun isChargerSupported(charger: ChargeLocation): Boolean {
        val country = charger.chargepriceData?.country
            ?: charger.address?.country ?: return false
        return when (charger.dataSource) {
            // list of countries as of 2021/06/30, according to
            // https://www.electrive.net/2021/06/30/enbw-expandiert-mit-ladenetz-in-drei-weitere-laender/
            "goingelectric" -> country in listOf(
                "Deutschland",
                "Österreich",
                "Schweiz",
                "Frankreich",
                "Belgien",
                "Niederlande",
                "Luxemburg",
                "Liechtenstein",
                "Italien",
            )
            "openchargemap" -> country in listOf(
                "DE",
                "AT",
                "CH",
                "FR",
                "BE",
                "NE",
                "LU",
                "LI",
                "IT"
            )
            else -> false
        }
    }
}