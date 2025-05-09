package net.vonforst.evmap.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.text.BidiFormatter
import androidx.core.content.ContextCompat
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import net.vonforst.evmap.model.Coordinate
import java.util.*
import kotlin.math.*

/**
 * Adds a certain distance in meters to a location. Approximate calculation.
 */
fun Location.plusMeters(dx: Double, dy: Double): Pair<Double, Double> {
    val lat = this.latitude + (180 / Math.PI) * (dx / earthRadiusM)
    val lon = this.longitude + (180 / Math.PI) * (dy / earthRadiusM) / cos(Math.toRadians(lat))
    return Pair(lat, lon)
}

fun LatLng.plusMeters(dx: Double, dy: Double): LatLng {
    val lat = this.latitude + (180 / Math.PI) * (dx / earthRadiusM)
    val lon = this.longitude + (180 / Math.PI) * (dy / earthRadiusM) / cos(Math.toRadians(lat))
    return LatLng(lat, lon)
}

const val earthRadiusM = 6378137.0

/**
 * Approximates a geodesic circle as an ellipse in geographical coordinates by giving its radius
 * in latitude and longitude in degrees.
 */
fun circleAsEllipse(lat: Double, lng: Double, radius: Double): Pair<Double, Double> {
    val radiusLat = (180 / Math.PI) * (radius / earthRadiusM)
    val radiusLon = (180 / Math.PI) * (radius / earthRadiusM) / cos(Math.toRadians(lat))
    return radiusLat to radiusLon
}

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
    val dLon = Math.toRadians(endLng) - Math.toRadians(startLng)
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

fun Coordinate.formatDMS(): String {
    return "${dms(lat, false)}, ${dms(lng, true)}"
}

fun Location.formatDMS(): String {
    return "${dms(latitude, false)}, ${dms(longitude, true)}"
}

private fun dms(value: Double, lon: Boolean): String {
    val hemisphere = if (lon) {
        if (value >= 0) "E" else "W"
    } else {
        if (value >= 0) "N" else "S"
    }
    val d = abs(value)
    val degrees = floor(d).toInt()
    val minutes = floor((d - degrees) * 60).toInt()
    val seconds = ((d - degrees) * 60 - minutes) * 60
    return "%d°%02d'%02.1f\"%s".format(Locale.ENGLISH, degrees, minutes, seconds, hemisphere)
}

fun Coordinate.formatDecimal(accuracy: Int = 6): String {
    return BidiFormatter.getInstance()
        .unicodeWrap("%.${accuracy}f, %.${accuracy}f".format(Locale.ENGLISH, lat, lng))
}

fun Location.formatDecimal(accuracy: Int = 6): String {
    return BidiFormatter.getInstance()
        .unicodeWrap("%.${accuracy}f, %.${accuracy}f".format(Locale.ENGLISH, latitude, longitude))
}

fun LatLngBounds.normalize() = LatLngBounds(
    LatLng(southwest.latitude, normalizeLongitude(southwest.longitude)),
    LatLng(northeast.latitude, normalizeLongitude(northeast.longitude)),
)

private fun normalizeLongitude(long: Double) =
    if (-180.0 <= long && long <= 180.0) long else (long + 180) % 360 - 180

fun LatLngBounds.crossesAntimeridian() = southwest.longitude > 0 && northeast.longitude < 0

fun LatLngBounds.splitAtAntimeridian(): Pair<LatLngBounds, LatLngBounds> {
    if (!crossesAntimeridian()) throw IllegalArgumentException("does not cross antimeridian")
    return LatLngBounds(
        LatLng(southwest.latitude, southwest.longitude),
        LatLng(northeast.latitude, 180.0),
    ) to LatLngBounds(
        LatLng(southwest.latitude, -180.0),
        LatLng(northeast.latitude, northeast.longitude),
    )
}