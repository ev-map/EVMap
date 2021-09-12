package net.vonforst.evmap.auto

import android.Manifest
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.CarHardwareLocation
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager


interface LocationAwareScreen {
    fun updateLocation(location: Location)
}

class CarAppService : androidx.car.app.CarAppService() {
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

class EVMapSession(val cas: CarAppService) : Session(), LifecycleObserver {
    var mapScreen: LocationAwareScreen? = null
        set(value) {
            field = value
            location?.let { value?.updateLocation(it) }
        }
    private var location: Location? = null
    private var locationService: CarLocationService? = null
    private val hardwareMan: CarHardwareManager by lazy {
        carContext.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, ibinder: IBinder) {
            val binder: CarLocationService.LocalBinder = ibinder as CarLocationService.LocalBinder
            locationService = binder.service
            locationService?.requestLocationUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return WelcomeScreen(carContext, this)
    }

    fun locationPermissionGranted() =
        ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra(CarLocationService.EXTRA_LOCATION) as Location?
            updateLocation(location)
        }
    }

    private fun updateLocation(location: Location?) {
        val mapScreen = mapScreen
        if (location != null && mapScreen != null) {
            mapScreen.updateLocation(location)
        }
        this.location = location
    }

    private fun onCarHardwareLocationReceived(loc: CarHardwareLocation) {
        updateLocation(loc.location.value)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun bindLocationService() {
        if (!locationPermissionGranted()) return
        if (supportsCarApiLevel3(carContext)) {
            val exec = ContextCompat.getMainExecutor(carContext)
            hardwareMan.carSensors.addCarHardwareLocationListener(
                CarSensors.UPDATE_RATE_NORMAL,
                exec,
                ::onCarHardwareLocationReceived
            )
        } else {
            cas.bindService(
                Intent(cas, CarLocationService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun unbindLocationService() {
        if (supportsCarApiLevel3(carContext)) {
            hardwareMan.carSensors.removeCarHardwareLocationListener(::onCarHardwareLocationReceived)
        } else {
            locationService?.let { service ->
                service.removeLocationUpdates()
                cas.unbindService(serviceConnection)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun registerBroadcastReceiver() {
        LocalBroadcastManager.getInstance(cas).registerReceiver(
            locationReceiver,
            IntentFilter(CarLocationService.ACTION_BROADCAST)
        );
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private fun unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(cas).unregisterReceiver(locationReceiver)
    }
}

