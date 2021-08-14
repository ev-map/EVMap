package net.vonforst.evmap.auto

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.Model
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import net.vonforst.evmap.R

class VehicleDataScreen(ctx: CarContext) : Screen(ctx) {
    private val hardwareMan = ctx.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
    private var model: Model? = null
    private var energyLevel: EnergyLevel? = null

    private val permissions = listOf(
        "com.google.android.gms.permission.CAR_FUEL"
    )

    override fun onGetTemplate(): Template {
        if (permissionsGranted()) {
            setupListeners()
        } else {
            Handler(Looper.getMainLooper()).post {
                screenManager.pushForResult(
                    PermissionScreen(
                        carContext,
                        R.string.auto_location_permission_needed,
                        permissions
                    )
                ) {
                    setupListeners()
                }
            }
        }

        val energyLevel = energyLevel
        val model = model

        return GridTemplate.Builder().apply {
            setTitle(
                if (model != null && model.manufacturer.value != null && model.name.value != null) {
                    "${model.manufacturer.value} ${model.name.value}"
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
                        addItem(
                            GridItem.Builder().apply {
                                setTitle("Battery")
                                energyLevel?.batteryPercent?.value?.let { percent ->
                                    setText("%.1f".format(percent))
                                    setImage(CarIcon.APP_ICON)
                                } ?: setLoading(true)
                            }.build()
                        )
                        addItem(
                            GridItem.Builder().apply {
                                setTitle("Fuel")
                                energyLevel?.fuelPercent?.value?.let { percent ->
                                    setText("%.1f".format(percent))
                                    setImage(CarIcon.APP_ICON)
                                } ?: setLoading(true)
                            }.build()
                        )
                    }.build()
                )
            }
        }.build()
    }

    private fun setupListeners() {
        val exec = ContextCompat.getMainExecutor(carContext)
        hardwareMan.carInfo.addEnergyLevelListener(exec) {
            this.energyLevel = it
            invalidate()
        }

        hardwareMan.carInfo.fetchModel(exec) {
            this.model = it
            invalidate()
        }
    }

    private fun permissionsGranted(): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(
                carContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
}