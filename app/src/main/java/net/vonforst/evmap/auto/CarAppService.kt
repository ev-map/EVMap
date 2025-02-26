package net.vonforst.evmap.auto

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.location.Location
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
import androidx.core.location.LocationListenerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.car2go.maps.model.LatLng
import net.vonforst.evmap.R
import net.vonforst.evmap.autocomplete.PlaceWithBounds
import net.vonforst.evmap.location.FusionEngine
import net.vonforst.evmap.location.LocationEngine
import net.vonforst.evmap.location.Priority
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.utils.checkFineLocationPermission
import org.acra.interaction.DialogInteraction


interface LocationAwareScreen {
    fun updateLocation(location: Location)
}

@ExperimentalCarApi
class CarAppService : androidx.car.app.CarAppService() {
    private val CHANNEL_ID = "car_location"
    private val NOTIFICATION_ID = 1000
    private val TAG = "CarAppService"
    private var foregroundStarted = false

    fun ensureForegroundService() {
        // we want to run as a foreground service to make sure we can use location
        try {
            if (!foregroundStarted) {
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, getNotification())
                foregroundStarted = true
                Log.i(TAG, "Started foreground service")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to start foreground service: ", e)
        }
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
            .setSmallIcon(R.drawable.ic_appicon_notification)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
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
    private val locationEngine: LocationEngine by lazy {
        FusionEngine(carContext)
    }

    private val hardwareMan: CarHardwareManager by lazy {
        carContext.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
    }

    private val prefs: PreferenceDataSource by lazy {
        PreferenceDataSource(carContext)
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        val mapScreen = if (supportsNewMapScreen(carContext)) {
            MapScreen(carContext, this)
        } else {
            LegacyMapScreen(carContext, this)
        }
        val screens = mutableListOf<Screen>(mapScreen)

        handleActionsIntent(intent)?.let {
            screens.add(it)
        }
        if (!prefs.dataSourceSet) {
            screens.add(
                ChooseDataSourceScreen(
                    carContext,
                    ChooseDataSourceScreen.Type.CHARGER_DATA_SOURCE,
                    initialChoice = true,
                    extraDesc = R.string.data_sources_description
                )
            )
        }
        if (!locationPermissionGranted()) {
            screens.add(
                PermissionScreen(
                    carContext,
                    R.string.auto_location_permission_needed,
                    listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            )
        }
        if (!prefs.privacyAccepted) {
            screens.add(
                AcceptPrivacyScreen(carContext)
            )
        }
        handleACRAIntent(intent)?.let {
            screens.add(it)
        }

        if (screens.size > 1) {
            val screenManager = carContext.getCarService(ScreenManager::class.java)
            for (i in 0 until screens.size - 1) {
                screenManager.push(screens[i])
            }
        }

        return screens.last()
    }

    private fun handleACRAIntent(intent: Intent): Screen? {
        return if (intent.hasExtra(DialogInteraction.EXTRA_REPORT_CONFIG)) {
            CrashReportScreen(carContext, intent)
        } else null
    }

    private fun handleActionsIntent(intent: Intent): Screen? {
        intent.data?.let {
            if (it.host == "find_charger") {
                val lat = it.getQueryParameter("latitude")?.toDouble()
                val lon = it.getQueryParameter("longitude")?.toDouble()
                val name = it.getQueryParameter("name")
                if (lat != null && lon != null) {
                    prefs.placeSearchResultAndroidAuto = PlaceWithBounds(LatLng(lat, lon), null)
                    prefs.placeSearchResultAndroidAutoName = name ?: "%.4f,%.4f".format(lat, lon)
                    return null
                } else if (name != null) {
                    val screen = PlaceSearchScreen(carContext, this, name)
                    return screen
                }
            }
        }
        return null
    }

    override fun onNewIntent(intent: Intent) {
        handleActionsIntent(intent)
    }

    private fun locationPermissionGranted() = carContext.checkFineLocationPermission()

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
        cas.ensureForegroundService()
        Log.i(TAG, "Requesting location updates")
        requestCarHardwareLocationUpdates()
        requestPhoneLocationUpdates()
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION])
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

    private val phoneLocationListener = LocationListenerCompat {
        this.updateLocation(it)
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    private fun requestPhoneLocationUpdates() {
        val location = locationEngine.getLastKnownLocation()
        updateLocation(location)
        locationEngine.requestLocationUpdates(
            Priority.HIGH_ACCURACY,
            1000,
            phoneLocationListener
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
        locationEngine.removeUpdates(phoneLocationListener)
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

