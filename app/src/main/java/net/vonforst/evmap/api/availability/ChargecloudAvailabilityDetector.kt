package net.vonforst.evmap.api.availability

import net.vonforst.evmap.api.iterator
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException

class ChargecloudAvailabilityDetector(
    client: OkHttpClient,
    private val operatorId: String
) : BaseAvailabilityDetector(client) {
    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val url =
            "https://app.chargecloud.de/emobility:ocpi/$operatorId/app/2.0/locations?latitude=${location.coordinates.lat}&longitude=${location.coordinates.lng}&radius=$radius&offset=0&limit=10"
        val json = JSONObject(httpGet(url))

        val statusMessage = json.getString("status_message")
        if (statusMessage != "Success") throw IOException(statusMessage)

        val data = json.getJSONArray("data")
        if (data.length() > 1) throw AvailabilityDetectorException(
            "found multiple candidates."
        )
        if (data.length() == 0) throw AvailabilityDetectorException(
            "no candidates found."
        )

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
                        getCorrespondingChargepoint(location.chargepointsMerged, type, power)
                            ?: throw AvailabilityDetectorException(
                                "Chargepoints from chargecloud API and goingelectric do not match."
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
        }



        if (chargepointStatus.keys == location.chargepointsMerged.toSet()) {
            return ChargeLocationStatus(
                chargepointStatus,
                "chargecloud.de"
            )
        } else {
            throw AvailabilityDetectorException(
                "Chargepoints from chargecloud API and goingelectric do not match."
            )
        }
    }

    private fun getType(string: String): String {
        return when (string) {
            "IEC_62196_T2" -> Chargepoint.TYPE_2_UNKNOWN
            "DOMESTIC_F" -> Chargepoint.SCHUKO
            "IEC_62196_T2_COMBO" -> Chargepoint.CCS_TYPE_2
            "CHADEMO" -> Chargepoint.CHADEMO
            else -> throw IllegalArgumentException("unrecognized type $string")
        }
    }
}