package net.vonforst.evmap.auto

import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.*
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.ui.CompassNeedle
import net.vonforst.evmap.ui.Gauge
import net.vonforst.evmap.utils.formatDecimal
import kotlin.math.min
import kotlin.math.roundToInt

@androidx.car.app.annotations.ExperimentalCarApi
class VehicleDataScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx),
    LocationAwareScreen, DefaultLifecycleObserver {
    private val carInfo =
        (ctx.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager).carInfo
    private val carSensors = carContext.patchedCarSensors
    private var model: Model? = null
    private var energyLevel: EnergyLevel? = null
    private var speed: Speed? = null
    private var heading: Compass? = null
    private var location: Location? = null
    private var gauge = Gauge((ctx.resources.displayMetrics.density * 128).roundToInt(), ctx)
    private var compass =
        CompassNeedle((ctx.resources.displayMetrics.density * 128).roundToInt(), ctx)
    private val maxSpeed = 160f / 3.6f // m/s, speed gauge will show max if speed is higher

    private val permissions = if (BuildConfig.FLAVOR_automotive == "automotive") {
        listOf(
            "android.car.permission.CAR_INFO",
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_ENERGY_PORTS",
            "android.car.permission.READ_CAR_DISPLAY_UNITS",
            "android.car.permission.CAR_SPEED"
        )
    } else {
        listOf(
            "com.google.android.gms.permission.CAR_FUEL",
            "com.google.android.gms.permission.CAR_SPEED"
        )
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        if (!permissionsGranted()) {
            Handler(Looper.getMainLooper()).post {
                screenManager.pushForResult(
                    PermissionScreen(
                        carContext,
                        R.string.auto_vehicle_data_permission_needed,
                        permissions,
                        finishApp = false
                    )
                ) {
                    setupListeners()
                }
            }
        }

        val energyLevel = energyLevel
        val model = model
        val speed = speed
        val location = location

        val compassHeading = heading?.orientations?.value?.get(0)
        val gpsHeading = if (location?.hasBearing() == true) location.bearing else null
        val heading = compassHeading ?: gpsHeading

        return GridTemplate.Builder().apply {
            setTitle(
                if (model != null && model.manufacturer.value != null && model.name.value != null) {
                    "${model.manufacturer.value} ${
                        getVehicleModel(
                            model.manufacturer.value,
                            model.name.value
                        )
                    }"
                } else {
                    carContext.getString(R.string.auto_vehicle_data)
                }
            )
            setHeaderAction(Action.BACK)
            if (!permissionsGranted()) {
                setLoading(true)
            } else {
                setSingleList(
                    ItemList.Builder().apply {
                        addItem(GridItem.Builder().apply {
                            setTitle(carContext.getString(R.string.auto_charging_level))
                            if (energyLevel == null) {
                                setLoading(true)
                            } else if (energyLevel.batteryPercent.value != null && energyLevel.fuelPercent.value != null) {
                                // both battery and fuel (Plug-in hybrid)
                                setText(
                                    "\uD83D\uDD0C %.0f %% ⛽ %.0f %%".format(
                                        energyLevel.batteryPercent.value,
                                        energyLevel.fuelPercent.value
                                    )
                                )
                                setImage(
                                    gauge.draw(
                                        energyLevel.batteryPercent.value,
                                        energyLevel.fuelPercent.value
                                    ).asCarIcon()
                                )
                            } else if (energyLevel.batteryPercent.value != null) {
                                // BEV
                                setText("%.0f %%".format(energyLevel.batteryPercent.value))
                                setImage(gauge.draw(energyLevel.batteryPercent.value).asCarIcon())
                            } else if (energyLevel.fuelPercent.value != null) {
                                // ICE
                                setText("⛽ %.0f %%".format(energyLevel.fuelPercent.value))
                                setImage(gauge.draw(energyLevel.fuelPercent.value).asCarIcon())
                            } else {
                                setText(carContext.getString(R.string.auto_no_data))
                                setImage(gauge.draw(0f).asCarIcon())
                            }
                        }.build())
                        addItem(GridItem.Builder().apply {
                            setTitle(carContext.getString(R.string.auto_range))
                            if (energyLevel == null) {
                                setLoading(true)
                            } else if (energyLevel.rangeRemainingMeters.value != null) {
                                setText(
                                    formatCarUnitDistance(
                                        energyLevel.rangeRemainingMeters.value,
                                        energyLevel.distanceDisplayUnit.value
                                    )
                                )
                                setImage(
                                    CarIcon.Builder(
                                        IconCompat.createWithResource(
                                            carContext,
                                            R.drawable.ic_car
                                        )
                                    ).build()
                                )
                            } else {
                                setText(carContext.getString(R.string.auto_no_data))
                                setImage(
                                    CarIcon.Builder(
                                        IconCompat.createWithResource(
                                            carContext,
                                            R.drawable.ic_car
                                        )
                                    ).build()
                                )
                            }
                        }.build())
                        addItem(GridItem.Builder().apply {
                            setTitle(carContext.getString(R.string.auto_speed))
                            if (speed == null) {
                                setLoading(true)
                            } else {
                                val rawSpeed = speed.rawSpeedMetersPerSecond.value
                                val displaySpeed = speed.displaySpeedMetersPerSecond.value
                                if (rawSpeed != null) {
                                    setText(
                                        formatCarUnitSpeed(
                                            rawSpeed,
                                            speed.speedDisplayUnit.value
                                        )
                                    )
                                    setImage(
                                        gauge.draw(min(rawSpeed / maxSpeed * 100, 100f)).asCarIcon()
                                    )
                                } else if (displaySpeed != null) {
                                    setText(
                                        formatCarUnitSpeed(
                                            speed.displaySpeedMetersPerSecond.value,
                                            speed.speedDisplayUnit.value
                                        )
                                    )
                                    setImage(
                                        gauge.draw(min(displaySpeed / maxSpeed * 100, 100f))
                                            .asCarIcon()
                                    )
                                } else {
                                    setText(carContext.getString(R.string.auto_no_data))
                                    setImage(gauge.draw(0f).asCarIcon())
                                }
                            }
                        }.build())
                        addItem(GridItem.Builder().apply {
                            setTitle(carContext.getString(R.string.auto_heading))
                            if (heading == null) {
                                setLoading(true)
                            } else {
                                val headingSource =
                                    if (compassHeading != null) carContext.getString(R.string.compass) else carContext.getString(
                                        R.string.gps
                                    )
                                setText("${heading.roundToInt()}° ($headingSource)")
                                setImage(
                                    compass.draw(heading).asCarIcon()
                                )
                            }
                        }.build())
                        addItem(GridItem.Builder().apply {
                            setTitle(carContext.getString(R.string.coordinates))
                            if (location == null) {
                                setLoading(true)
                            } else {
                                val dms = location.formatDecimal(4)
                                setText(dms)
                                setImage(
                                    CarIcon.Builder(
                                        IconCompat.createWithResource(
                                            carContext,
                                            R.drawable.ic_location
                                        )
                                    ).setTint(CarColor.DEFAULT).build()
                                )
                            }
                        }.build())
                    }.build()
                )
            }
        }.build()
    }

    private fun onEnergyLevelUpdated(energyLevel: EnergyLevel) {
        this.energyLevel = energyLevel
        invalidate()
    }

    private fun onSpeedUpdated(speed: Speed) {
        this.speed = speed
        invalidate()
    }

    private fun onCompassUpdated(compass: Compass) {
        this.heading = compass
        invalidate()
    }

    override fun onResume(owner: LifecycleOwner) {
        setupListeners()
        session.mapScreen = this
    }

    private fun setupListeners() {
        if (!permissionsGranted()) return

        println("Setting up energy level listener")

        val exec = ContextCompat.getMainExecutor(carContext)
        carInfo.addEnergyLevelListener(exec, ::onEnergyLevelUpdated)
        carInfo.addSpeedListener(exec, ::onSpeedUpdated)
        carSensors.addCompassListener(
            CarSensors.UPDATE_RATE_NORMAL,
            exec,
            ::onCompassUpdated
        )

        carInfo.fetchModel(exec) {
            this.model = it
            invalidate()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        removeListeners()
        session.mapScreen = null
    }

    private fun removeListeners() {
        println("Removing energy level listener")
        carInfo.removeEnergyLevelListener(::onEnergyLevelUpdated)
        carInfo.removeSpeedListener(::onSpeedUpdated)
        carSensors.removeCompassListener(::onCompassUpdated)
    }

    private fun permissionsGranted(): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(
                carContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }

    override fun updateLocation(location: Location) {
        this.location = location
    }
}