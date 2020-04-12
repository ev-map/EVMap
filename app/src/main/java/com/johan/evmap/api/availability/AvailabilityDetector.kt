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