package net.vonforst.evmap.auto

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.validation.HostValidator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import net.vonforst.evmap.R
import net.vonforst.evmap.utils.checkAnyLocationPermission


interface LocationAwareScreen {
    fun updateLocation(location: Location)
}

@ExperimentalCarApi
class CarAppService : androidx.car.app.CarAppService() {
    private val CHANNEL_ID = "car_location"
    private val NOTIFICATION_ID = 1000

    override fun onCreate() {
        super.onCreate()

        // we want to run as a foreground service to make sure we can use location
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, getNotification())
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            // Create the channel for the notification
            val mChannel =
                NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)

            // Set the Notification Channel for the Notification Manager.
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    private fun getNotification(): Notification {
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentText(getString(R.string.auto_location_service))
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setPriority(NotificationManagerCompat.IMPORTANCE_HIGH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker(getString(R.string.auto_location_service))
            .setWhen(System.currentTimeMillis())

        return builder.build()
    }

    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return EVMapSession(this)
    }
}

@ExperimentalCarApi
class EVMapSession(val cas: CarAppService) : Session(), DefaultLifecycleObserver {
    private val TAG = "EVMapSession"
    var mapScreen: LocationAwareScreen? = null
        set(value) {
            field = value
            location?.let { value?.updateLocation(it) }
        }
    private var location: Location? = null
    private val locationManager: LocationManager by lazy {
        carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val hardwareMan: CarHardwareManager by lazy {
        carContext.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        val mapScreen = MapScreen(carContext, this)

        if (!locationPermissionGranted()) {
            val screenManager = carContext.getCarService(ScreenManager::class.java)
            screenManager.push(mapScreen)
            return PermissionScreen(
                carContext,
                R.string.auto_location_permission_needed,
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        return mapScreen
    }

    fun locationPermissionGranted() = carContext.checkAnyLocationPermission()

    private fun updateLocation(location: Location?) {
        Log.d(TAG, "Received location: $location")
        val mapScreen = mapScreen
        if (location != null && mapScreen != null) {
            mapScreen.updateLocation(location)
        }
        this.location = location
    }

    override fun onStart(owner: LifecycleOwner) {
        requestLocationUpdates()
    }

    override fun onStop(owner: LifecycleOwner) {
        removeLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    fun requestLocationUpdates() {
        if (!locationPermissionGranted()) return
        Log.i(TAG, "Requesting location updates")
        requestCarHardwareLocationUpdates()
        requestPhoneLocationUpdates()
    }

    private fun requestCarHardwareLocationUpdates() {
        if (supportsCarApiLevel3(carContext)) {
            val exec = ContextCompat.getMainExecutor(carContext)
            hardwareMan.carSensors.addCarHardwareLocationListener(
                CarSensors.UPDATE_RATE_NORMAL,
                exec,
                ::onCarHardwareLocationReceived
            )
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    private fun requestPhoneLocationUpdates() {
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        updateLocation(location)
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000,
            1f,
            this::updateLocation
        )
    }


    @SuppressLint("MissingPermission")
    private fun removeLocationUpdates() {
        if (!locationPermissionGranted()) return
        removeCarHardwareLocationUpdates()
        removePhoneLocationUpdates()
    }

    private fun removeCarHardwareLocationUpdates() {
        if (supportsCarApiLevel3(carContext)) {
            hardwareMan.carSensors.removeCarHardwareLocationListener(::onCarHardwareLocationReceived)
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    private fun removePhoneLocationUpdates() {
        locationManager.removeUpdates(this::updateLocation)
    }

    @SuppressLint("MissingPermission")
    private fun onCarHardwareLocationReceived(loc: CarHardwareLocation) {
        if (loc.location.status == CarValue.STATUS_SUCCESS && loc.location.value != null) {
            updateLocation(loc.location.value)

            // we successfully received a location from the car hardware,
            // so we don't need the smartphone location anymore.
            removePhoneLocationUpdates()
        }
    }
}

