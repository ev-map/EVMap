package net.vonforst.evmap.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import kotlin.math.*

/**
 * Adds a certain distance in meters to a location. Approximate calculation.
 */
fun Location.plusMeters(dx: Double, dy: Double): Pair<Double, Double> {
    val lat = this.latitude + (180 / Math.PI) * (dx / 6378137.0)
    val lon = this.longitude + (180 / Math.PI) * (dy / 6378137.0) / cos(Math.toRadians(lat))
    return Pair(lat, lon)
}

fun LatLng.plusMeters(dx: Double, dy: Double): LatLng {
    val lat = this.latitude + (180 / Math.PI) * (dx / 6378137.0)
    val lon = this.longitude + (180 / Math.PI) * (dy / 6378137.0) / cos(Math.toRadians(lat))
    return LatLng(lat, lon)
}

const val earthRadiusM = 6378137.0

/**
 * Calculates the distance between two points on Earth in meters.
 * Latitude and longitude should be given in degrees.
 */
fun distanceBetween(
    startLatitude: Double, startLongitude: Double,
    endLatitude: Double, endLongitude: Double
): Double {
    // see https://rosettacode.org/wiki/Haversine_formula#Java
    val dLat = Math.toRadians(endLatitude - startLatitude)
    val dLon = Math.toRadians(endLongitude - startLongitude)
    val originLat = Math.toRadians(startLatitude)
    val destinationLat = Math.toRadians(endLatitude)

    val a = sin(dLat / 2).pow(2.0) + sin(dLon / 2).pow(2.0) * cos(originLat) * cos(destinationLat)
    val c = 2 * asin(sqrt(a))
    return earthRadiusM * c
}


fun bearingBetween(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Double {
    val dLon = Math.toRadians(-endLng) - Math.toRadians(-startLng)
    val originLat = Math.toRadians(startLat)
    val destinationLat = Math.toRadians(endLat)

    return Math.toDegrees(
        atan2(
            sin(dLon) * cos(destinationLat),
            cos(originLat) * sin(destinationLat) - sin(originLat) * cos(destinationLat) * cos(dLon)
        )
    )
}


fun headingDiff(h1: Double, h2: Double): Double {
    return (h1 - h2 + 540) % 360 - 180
}


fun getLocationFromIntent(intent: Intent): List<Double>? {
    val pos = intent.data?.schemeSpecificPart?.split("?")?.get(0)
    var coords = stringToCoords(pos)
    if (coords != null) {
        return coords
    }
    val query = intent.data?.query?.split("=")?.get(1)
    coords = stringToCoords(query)
    if (coords != null) {
        return coords
    } else {
        return null
    }
}

internal fun stringToCoords(s: String?): List<Double>? {
    if (s == null) return null

    val coords = s.split(",").mapNotNull { it.toDoubleOrNull() }
    return if (coords.size == 2 && !(coords[0] == 0.0 && coords[1] == 0.0)) {
        coords
    } else {
        null
    }
}

fun boundingBox(pos: LatLng, sizeMeters: Double): LatLngBounds {
    return LatLngBounds(
        pos.plusMeters(-sizeMeters, -sizeMeters),
        pos.plusMeters(sizeMeters, sizeMeters)
    )
}

fun Context.checkAnyLocationPermission() = ContextCompat.checkSelfPermission(
    this,
    Manifest.permission.ACCESS_FINE_LOCATION
) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

fun Context.checkFineLocationPermission() = ContextCompat.checkSelfPermission(
    this,
    Manifest.permission.ACCESS_FINE_LOCATION
) == PackageManager.PERMISSION_GRANTED