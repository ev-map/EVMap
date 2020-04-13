package com.johan.evmap.api.availability

import com.facebook.stetho.okhttp3.StethoInterceptor
import com.johan.evmap.api.await
import com.johan.evmap.api.goingelectric.ChargeLocation
import com.johan.evmap.api.goingelectric.Chargepoint
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

interface AvailabilityDetector {
    suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus
}

abstract class BaseAvailabilityDetector(private val client: OkHttpClient) : AvailabilityDetector {
    protected suspend fun httpGet(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) throw IOException(response.message())

        val str = response.body()!!.string()
        return str
    }

    protected fun getCorrespondingChargepoint(
        cps: Iterable<Chargepoint>, type: String, power: Double
    ): Chargepoint? {
        var filter = cps.filter {
            it.type == type
        }
        if (filter.size > 1) {
            filter = filter.filter {
                if (power > 0) {
                    it.power == power
                } else true
            }
            // TODO: handle not matching powers
            /*if (filter.isEmpty()) {
                filter = listOfNotNull(cps.minBy {
                    abs(it.power - power)
                })
            }*/
        }
        return filter.getOrNull(0)
    }


    protected fun matchChargepoints(
        connectors: Map<Long, Pair<Double, String>>,
        chargepoints: List<Chargepoint>
    ): Map<Chargepoint, Set<Long>> {
        // iterate over each connector type
        val types = connectors.map { it.value.second }.distinct().toSet()
        val geTypes = chargepoints.map { it.type }.distinct().toSet()
        if (types != geTypes) throw AvailabilityDetectorException("chargepoints do not match")
        return types.flatMap { type ->
            // find connectors of this type
            val connsOfType = connectors.filter { it.value.second == type }
            // find powers this connector is available as
            val powers = connsOfType.map { it.value.first }.distinct().sorted()
            // find corresponding powers in GE data
            val gePowers =
                chargepoints.filter { it.type == type }.map { it.power }.distinct().sorted()

            // if the distinct number of powers is the same, try to match.
            if (powers.size == gePowers.size) {
                gePowers.zip(powers).map { (gePower, power) ->
                    val chargepoint = chargepoints.find { it.type == type && it.power == gePower }!!
                    val ids = connsOfType.filter { it.value.first == power }.keys
                    chargepoint to ids
                }
            } else {
                throw AvailabilityDetectorException("chargepoints do not match")
            }
        }.toMap()
    }
}

data class ChargeLocationStatus(
    val status: Map<Chargepoint, List<ChargepointStatus>>,
    val source: String
)

enum class ChargepointStatus {
    AVAILABLE, UNKNOWN, CHARGING, OCCUPIED, FAULTED
}

class AvailabilityDetectorException(message: String) : Exception(message)

private val okhttp = OkHttpClient.Builder()
    .addNetworkInterceptor(StethoInterceptor())
    .readTimeout(10, TimeUnit.SECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .build()
val availabilityDetectors = listOf(
    NewMotionAvailabilityDetector(okhttp)
    /*ChargecloudAvailabilityDetector(
        okhttp,
        "606a0da0dfdd338ee4134605653d4fd8"
    ), // Maingau
    ChargecloudAvailabilityDetector(
        okhttp,
        "6336fe713f2eb7fa04b97ff6651b76f8"
    )  // SW Kiel*/
)