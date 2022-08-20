package net.vonforst.evmap.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.location.LocationListenerCompat

/**
 * Location engine that fuses GPS and network locations.
 *
 * Simplified version of
 * https://github.com/lostzen/lost/blob/master/lost/src/main/java/com/mapzen/android/lost/internal/FusionEngine.java
 */
class FusionEngine(context: Context) : LocationEngine(context),
    LocationListenerCompat {

    /**
     * Location updates more than 60 seconds old are considered stale.
     */
    private val RECENT_UPDATE_THRESHOLD_IN_MILLIS = (60 * 1000).toLong()
    private val RECENT_UPDATE_THRESHOLD_IN_NANOS = RECENT_UPDATE_THRESHOLD_IN_MILLIS * 1000000
    private val TAG = FusionEngine::class.java.simpleName

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var gpsLocation: Location? = null
    private var networkLocation: Location? = null

    private val supportsSystemFusedProvider: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.allProviders.contains(
            LocationManager.FUSED_PROVIDER
        )

    override fun getLastKnownLocation(): Location? {
        if (supportsSystemFusedProvider) {
            try {
                return locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
            } catch (e: SecurityException) {
                Log.e(TAG, "Permissions not granted for fused provider", e)
            }
        }

        val minTime = SystemClock.elapsedRealtimeNanos() - RECENT_UPDATE_THRESHOLD_IN_NANOS
        var bestLocation: Location? = null
        var bestAccuracy = Float.MAX_VALUE
        var bestTime = Long.MIN_VALUE
        for (provider in locationManager.allProviders) {
            try {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null) {
                    val accuracy = location.accuracy
                    val time = location.elapsedRealtimeNanos
                    if (time > minTime && accuracy < bestAccuracy) {
                        bestLocation = location
                        bestAccuracy = accuracy
                        bestTime = time
                    } else if (time < minTime && bestAccuracy == Float.MAX_VALUE && time > bestTime) {
                        bestLocation = location
                        bestTime = time
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permissions not granted for provider: $provider", e)
            }
        }
        return bestLocation
    }

    override fun enable() {
        var networkInterval = Long.MAX_VALUE
        var gpsInterval = Long.MAX_VALUE
        var passiveInterval = Long.MAX_VALUE
        for ((priority, interval) in requests) {
            when (priority) {
                Priority.HIGH_ACCURACY -> {
                    if (interval < gpsInterval) {
                        gpsInterval = interval
                    }
                    if (interval < networkInterval) {
                        networkInterval = interval
                    }
                }
                Priority.BALANCED_POWER_ACCURACY, Priority.LOW_POWER -> if (interval < networkInterval) {
                    networkInterval = interval
                }
                Priority.NO_POWER -> if (interval < passiveInterval) {
                    passiveInterval = interval
                }
            }
        }

        if (supportsSystemFusedProvider && gpsInterval < Long.MAX_VALUE) {
            try {
                enableFused(gpsInterval)
                checkLastKnownFused()
                return
            } catch (e: SecurityException) {
                Log.e(TAG, "Permissions not granted for fused provider", e)
            }
        }

        var checkGps = false
        if (gpsInterval < Long.MAX_VALUE) {
            enableGps(gpsInterval)
            checkGps = true
        }
        if (networkInterval < Long.MAX_VALUE) {
            enableNetwork(networkInterval)
            if (checkGps) {
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNetwork =
                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (lastGps != null && lastNetwork != null) {
                    val useGps = lastGps.isBetterThan(lastNetwork)
                    if (useGps) {
                        checkLastKnownGps()
                    } else {
                        checkLastKnownNetwork()
                    }
                } else if (lastGps != null) {
                    checkLastKnownGps()
                } else {
                    checkLastKnownNetwork()
                }
            } else {
                checkLastKnownNetwork()
            }
        }
        if (passiveInterval < Long.MAX_VALUE) {
            enablePassive(passiveInterval)
            checkLastKnownPassive()
        }
    }

    @Throws(SecurityException::class)
    override fun disable() {
        locationManager.removeUpdates(this)
    }

    @Throws(SecurityException::class)
    private fun enableGps(interval: Long) {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                interval,
                0f,
                this,
                looper
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unable to register for GPS updates.", e)
        }
    }

    @Throws(SecurityException::class)
    private fun enableNetwork(interval: Long) {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                interval,
                0f,
                this,
                looper
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unable to register for network updates.", e)
        }
    }

    @Throws(SecurityException::class)
    private fun enablePassive(interval: Long) {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER,
                interval,
                0f,
                this,
                looper
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unable to register for passive updates.", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Throws(SecurityException::class)
    private fun enableFused(interval: Long) {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.FUSED_PROVIDER,
                interval,
                0f,
                this,
                looper
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unable to register for passive updates.", e)
        }
    }

    private fun checkLastKnownGps() {
        checkLastKnownAndNotify(LocationManager.GPS_PROVIDER)
    }

    private fun checkLastKnownNetwork() {
        checkLastKnownAndNotify(LocationManager.NETWORK_PROVIDER)
    }

    private fun checkLastKnownPassive() {
        checkLastKnownAndNotify(LocationManager.PASSIVE_PROVIDER)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkLastKnownFused() {
        checkLastKnownAndNotify(LocationManager.FUSED_PROVIDER)
    }

    private fun checkLastKnownAndNotify(provider: String) {
        val location = locationManager.getLastKnownLocation(provider)
        location?.let { onLocationChanged(it) }
    }

    override fun onLocationChanged(location: Location) {
        if (LocationManager.FUSED_PROVIDER == location.provider) {
            requests.forEach { it.listener.onLocationChanged(location) }
        } else if (LocationManager.GPS_PROVIDER == location.provider) {
            gpsLocation = location
            if (gpsLocation.isBetterThan(networkLocation)) {
                requests.forEach { it.listener.onLocationChanged(location) }
            }
        } else if (LocationManager.NETWORK_PROVIDER == location.provider) {
            networkLocation = location
            if (networkLocation.isBetterThan(gpsLocation)) {
                requests.forEach { it.listener.onLocationChanged(location) }
            }
        }
    }

    private fun Location?.isBetterThan(other: Location?): Boolean {
        if (this == null) {
            return false
        }
        if (other == null) {
            return true
        }
        if (this.elapsedRealtimeNanos
            > other.elapsedRealtimeNanos + RECENT_UPDATE_THRESHOLD_IN_NANOS
        ) {
            return true
        }
        if (!this.hasAccuracy()) {
            return false
        }
        return if (!other.hasAccuracy()) {
            true
        } else this.accuracy < other.accuracy
    }
}