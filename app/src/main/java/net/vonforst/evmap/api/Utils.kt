package net.vonforst.evmap.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resumeWithException

operator fun <T> JSONArray.iterator(): Iterator<T> =
    (0 until length()).asSequence().map { get(it) as T }.iterator()

@ExperimentalCoroutinesApi
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) {}
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                //Ignore cancel exception
            }
        }
    }
}

const val earthRadiusKm: Double = 6372.8

/**
 * Calculates the distance between two points on Earth in meters.
 * Latitude and longitude should be given in degrees.
 */
fun distanceBetween(
    startLatitude: Double, startLongitude: Double,
    endLatitude: Double, endLongitude: Double
): Double {
    // see https://rosettacode.org/wiki/Haversine_formula#Java
    val dLat = Math.toRadians(endLatitude - startLatitude);
    val dLon = Math.toRadians(endLongitude - startLongitude);
    val originLat = Math.toRadians(startLatitude);
    val destinationLat = Math.toRadians(endLatitude);

    val a = Math.pow(Math.sin(dLat / 2), 2.toDouble()) + Math.pow(
        Math.sin(dLon / 2),
        2.toDouble()
    ) * Math.cos(originLat) * Math.cos(destinationLat);
    val c = 2 * Math.asin(Math.sqrt(a));
    return earthRadiusKm * c * 1000;
}