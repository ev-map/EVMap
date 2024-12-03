package net.vonforst.evmap.api.availability

import android.content.Context
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import net.vonforst.evmap.R
import net.vonforst.evmap.model.ChargeLocation
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.time.Instant

internal class InstantStringAdapter {
    @FromJson
    fun fromJson(value: String?): Instant? = value?.let {
        Instant.parse(value)
    }

    @ToJson
    fun toJson(value: Instant?): String? = value?.toString()
}

interface NobilRealtimeApi {
    @GET("{nobilId}")
    suspend fun getAvailability(
        @Path("nobilId") nobilId: String,
        @Header("X-Api-Key") apiKey: String
    ): List<NobilChargepointState>

    companion object {
        fun create(client: OkHttpClient): NobilRealtimeApi {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.ev-map.app/nobil/api/realtime/")
                .addConverterFactory(
                    MoshiConverterFactory.create(
                        Moshi.Builder().add(InstantStringAdapter()).build()
                    )
                )
                .client(client)
                .build()
            return retrofit.create(NobilRealtimeApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class NobilChargepointState(
    val evseUid: String,
    val status: String,
    val timestamp: Instant
)

class NobilAvailabilityDetector(client: OkHttpClient, context: Context) :
    BaseAvailabilityDetector(client) {
    val api = NobilRealtimeApi.create(client)
    val apiKey = context.getString(R.string.evmap_key)

    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val nobilId = when (location.address?.country) {
            "Norway" -> "NOR"
            "Sweden" -> "SWE"
            else -> throw AvailabilityDetectorException("nobil: unsupported country")
        } + "_%05d".format(location.id)

        val availability = api.getAvailability(nobilId, apiKey)
        if (availability.isEmpty()) {
            throw AvailabilityDetectorException("nobil: no real-time data available")
        }
        return ChargeLocationStatus(
            location.chargepointsMerged.associateWith { cp ->
                cp.evseUIds!!.map { evseUId ->
                    when (availability.find { it.evseUid == evseUId }?.status) {
                        "AVAILABLE" -> ChargepointStatus.AVAILABLE
                        "BLOCKED" -> ChargepointStatus.OCCUPIED
                        "CHARGING" -> ChargepointStatus.CHARGING
                        "INOPERATIVE" -> ChargepointStatus.FAULTED
                        "OUTOFORDER" -> ChargepointStatus.FAULTED
                        "PLANNED" -> ChargepointStatus.FAULTED
                        "REMOVED" -> ChargepointStatus.FAULTED
                        "RESERVED" -> ChargepointStatus.OCCUPIED
                        "UNKNOWN" -> ChargepointStatus.UNKNOWN
                        else -> ChargepointStatus.UNKNOWN
                    }
                }
            },
            "Nobil",
            location.chargepointsMerged.associateWith { cp ->
                if (cp.evseIds != null) cp.evseIds.map { it ?: "??" } else listOf()
            },
            lastChange = location.chargepointsMerged.associateWith { cp ->
                cp.evseUIds!!.map { evseUId ->
                    availability.find { it.evseUid == evseUId }?.timestamp
                }
            }
        )
    }

    override fun isChargerSupported(charger: ChargeLocation): Boolean {
        return when (charger.dataSource) {
            "nobil" -> charger.chargepoints.any { it.evseUIds?.isNotEmpty() == true }
            else -> false
        }
    }
}
