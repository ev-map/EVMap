package com.johan.evmap.api

import com.facebook.stetho.okhttp3.StethoInterceptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val radius = 200  // max radius in meters

interface AvailabilityDetector {
    suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus
}

data class ChargeLocationStatus(
    val status: Map<Chargepoint, List<ChargepointStatus>>,
    val source: String
)

enum class ChargepointStatus {
    AVAILABLE, UNKNOWN, CHARGING, OCCUPIED, FAULTED
}

class AvailabilityDetectorException(message: String) : Exception(message)

class ChargecloudAvailabilityDetector(
    private val client: OkHttpClient,
    private val operatorId: String
) : AvailabilityDetector {
    @ExperimentalCoroutinesApi
    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val url =
            "https://app.chargecloud.de/emobility:ocpi/$operatorId/app/2.0/locations?latitude=${location.coordinates.lat}&longitude=${location.coordinates.lng}&radius=$radius&offset=0&limit=10"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) throw IOException(response.message())

        val json = JSONObject(response.body()!!.string())

        val statusMessage = json.getString("status_message")
        if (statusMessage != "Success") throw IOException(statusMessage)

        val data = json.getJSONArray("data")
        if (data.length() > 1) throw AvailabilityDetectorException("found multiple candidates.")
        if (data.length() == 0) throw AvailabilityDetectorException("no candidates found.")

        val evses = data.getJSONObject(0).getJSONArray("evses")
        val chargepointStatus = mutableMapOf<Chargepoint, List<ChargepointStatus>>()
        evses.iterator<JSONObject>().forEach { evse ->
            evse.getJSONArray("connectors").iterator<JSONObject>().forEach connector@{ connector ->
                val type = getType(connector.getString("standard"))
                val power = connector.getDouble("max_power")
                val status = ChargepointStatus.valueOf(connector.getString("status"))

                var chargepoint = getCorrespondingChargepoint(chargepointStatus.keys, type, power)
                val statusList: List<ChargepointStatus>
                if (chargepoint == null) {
                    // find corresponding chargepoint from goingelectric to get correct power
                    val geChargepoint =
                        getCorrespondingChargepoint(location.chargepoints, type, power)
                            ?: throw AvailabilityDetectorException("Chargepoints from chargecloud API and goingelectric do not match.")
                    chargepoint = Chargepoint(type, geChargepoint.power, 1)
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



        if (chargepointStatus.keys == location.chargepoints.toSet()) {
            return ChargeLocationStatus(chargepointStatus, "chargecloud.de")
        } else {
            throw AvailabilityDetectorException("Chargepoints from chargecloud API and goingelectric do not match.")
        }
    }

    private fun getCorrespondingChargepoint(
        cps: Iterable<Chargepoint>, type: String, power: Double
    ): Chargepoint? {
        var filter = cps.filter {
            it.type == type &&
                    if (power > 0) {
                        it.power in power - 2..power + 2
                    } else true
        }
        if (filter.size > 1) {
            filter = cps.filter {
                it.type == type &&
                        if (power > 0) {
                            it.power == power
                        } else true
            }
        }
        return filter.getOrNull(0)
    }

    private fun getType(string: String): String {
        return when (string) {
            "IEC_62196_T2" -> Chargepoint.TYPE_2
            "DOMESTIC_F" -> Chargepoint.SCHUKO
            "IEC_62196_T2_COMBO" -> Chargepoint.CCS
            "CHADEMO" -> Chargepoint.CHADEMO
            else -> throw IllegalArgumentException("unrecognized type $string")
        }
    }
}

private val okhttp = OkHttpClient.Builder()
    .addNetworkInterceptor(StethoInterceptor())
    .readTimeout(10, TimeUnit.SECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .build()
val availabilityDetectors = listOf(
    ChargecloudAvailabilityDetector(okhttp, "606a0da0dfdd338ee4134605653d4fd8"), // Maingau
    ChargecloudAvailabilityDetector(okhttp, "6336fe713f2eb7fa04b97ff6651b76f8")  // SW Kiel
)