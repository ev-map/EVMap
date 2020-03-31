package com.johan.evmap.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val radius = 100  // max radius in meters

interface AvailabilityDetector {
    suspend fun getAvailability(location: ChargeLocation): Map<Chargepoint, List<ChargepointStatus>>
}

enum class ChargepointStatus {
    AVAILABLE, UNKNOWN, CHARGING
}

class ChargecloudAvailabilityDetector(private val client: OkHttpClient,
                                      private val operatorId: String): AvailabilityDetector {
    @ExperimentalCoroutinesApi
    override suspend fun getAvailability(location: ChargeLocation): Map<Chargepoint, List<ChargepointStatus>> {
        val url = "https://app.chargecloud.de/emobility:ocpi/$operatorId/app/2.0/locations?latitude=${location.coordinates.lat}&longitude=${location.coordinates.lng}&radius=$radius&offset=0&limit=10"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) throw IOException(response.message())

        val json = JSONObject(response.body()!!.string())

        val statusMessage = json.getString("status_message")
        if (statusMessage != "Success") throw IOException(statusMessage)

        val data = json.getJSONArray("data")
        if (data.length() > 1) throw IOException("found multiple candidates.")
        if (data.length() == 0) throw IOException("no candidates found.")

        val evses = data.getJSONObject(0).getJSONArray("evses")
        val chargepointStatus = mutableMapOf<Chargepoint, List<ChargepointStatus>>()
        evses.iterator<JSONObject>().forEach { evse ->
            evse.getJSONArray("connectors").iterator<JSONObject>().forEach connector@{ connector ->
                val type = getType(connector.getString("standard"))
                val power = connector.getDouble("max_power")
                val status = ChargepointStatus.valueOf(connector.getString("status"))
                if (type == null) return@connector

                var chargepoint = chargepointStatus.keys.filter {
                    it.type == type
                    it.power == power
                }.getOrNull(0)
                val statusList: List<ChargepointStatus>
                if (chargepoint == null) {
                    chargepoint = Chargepoint(type, power, 1)
                    statusList = listOf(status)
                } else {
                    val previousStatus = chargepointStatus[chargepoint]!!
                    statusList = previousStatus + listOf(status)
                    chargepointStatus.remove(chargepoint)
                    chargepoint =
                        Chargepoint(chargepoint.type, chargepoint.power, chargepoint.count + 1)
                }

                chargepointStatus[chargepoint] = statusList
            }
        }
        return chargepointStatus
    }

    private fun getType(string: String): String? {
        return when (string) {
            "IEC_62196_T2" -> Chargepoint.TYPE_2
            "DOMESTIC_F" -> Chargepoint.SCHUKO
            "IEC_62196_T2_COMBO" -> Chargepoint.CCS
            "CHADEMO" -> Chargepoint.CHADEMO
            else -> null
        }
    }
}

private val okhttp = OkHttpClient.Builder()
    .readTimeout(10, TimeUnit.SECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .build()
val availabilityDetectors = listOf(
    ChargecloudAvailabilityDetector(okhttp, "6336fe713f2eb7fa04b97ff6651b76f8")
)