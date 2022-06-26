package net.vonforst.evmap.api.availability

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ChargecloudApi {
    @GET("locations")
    suspend fun getData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius") radius: Int,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 10
    ): ChargecloudResponse

    @JsonClass(generateAdapter = true)
    data class ChargecloudResponse(
        val data: List<ChargecloudLocation>
    )

    @JsonClass(generateAdapter = true)
    data class ChargecloudLocation(
        val coordinates: ChargecloudCoordinates,
        val evses: List<ChargecloudEvse>,
        @Json(name = "distance_in_m") val distanceInM: String
    )

    @JsonClass(generateAdapter = true)
    data class ChargecloudCoordinates(val latitude: Double, val longitude: Double)

    @JsonClass(generateAdapter = true)
    data class ChargecloudEvse(
        val id: String,
        val status: String,
        val connectors: List<ChargecloudConnector>
    )

    @JsonClass(generateAdapter = true)
    data class ChargecloudConnector(
        val id: Long,
        val standard: String,
        @Json(name = "max_power") val maxPower: Double,
        val status: String
    )

    companion object {
        fun create(client: OkHttpClient, baseUrl: String? = null): ChargecloudApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(MoshiConverterFactory.create())
                .client(client)
                .build()
            return retrofit.create(ChargecloudApi::class.java)
        }
    }
}

abstract class ChargecloudAvailabilityDetector(
    client: OkHttpClient
) : BaseAvailabilityDetector(client) {
    protected abstract val operatorId: String

    private val api: ChargecloudApi by lazy {
        val baseUrl = "https://app.chargecloud.de/emobility:ocpi/$operatorId/app/2.0/"
        ChargecloudApi.create(client, baseUrl)
    }

    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val data = api.getData(location.coordinates.lat, location.coordinates.lng, radius)

        val nearest = data.data.minByOrNull { it.distanceInM.toDouble() }
            ?: throw AvailabilityDetectorException("no candidates found.")

        val chargecloudConnectors = mutableMapOf<Long, Pair<Double, String>>()
        val chargecloudStatus = mutableMapOf<Long, ChargepointStatus>()

        nearest.evses.flatMap { it.connectors }.forEach {
            val id = it.id
            val power = it.maxPower
            val type = getType(it.standard)
            val status = when (it.status) {
                "OUTOFORDER" -> ChargepointStatus.FAULTED
                "AVAILABLE" -> ChargepointStatus.AVAILABLE
                "CHARGING" -> ChargepointStatus.CHARGING
                "UNKNOWN" -> ChargepointStatus.UNKNOWN
                else -> ChargepointStatus.UNKNOWN
            }
            chargecloudConnectors.put(id, power to type)
            chargecloudStatus.put(id, status)
        }

        val match = matchChargepoints(chargecloudConnectors, location.chargepointsMerged)
        val chargepointStatus = match.mapValues { entry ->
            entry.value.map { chargecloudStatus[it]!! }
        }

        return ChargeLocationStatus(
            chargepointStatus,
            "chargecloud.de"
        )
    }

    private fun getType(string: String): String {
        return when (string) {
            "IEC_62196_T2" -> Chargepoint.TYPE_2_UNKNOWN
            "DOMESTIC_F" -> Chargepoint.SCHUKO
            "IEC_62196_T2_COMBO" -> Chargepoint.CCS_TYPE_2
            "CHADEMO" -> Chargepoint.CHADEMO
            else -> "unknown"
        }
    }
}

class RheinenergieAvailabilityDetector(client: OkHttpClient) :
    ChargecloudAvailabilityDetector(client) {
    override val operatorId = "c4ce9bb82a86766833df8a4818fa1b5c"

    override fun isChargerSupported(charger: ChargeLocation): Boolean {
        val network = charger.chargepriceData?.network ?: charger.network ?: return false
        return when (charger.dataSource) {
            "goingelectric" -> network == "RheinEnergie"
            "openchargemap" -> network == "72"
            else -> false
        }
    }
}

// "606a0da0dfdd338ee4134605653d4fd8" Maingau
// "6336fe713f2eb7fa04b97ff6651b76f8" SW Kiel*/