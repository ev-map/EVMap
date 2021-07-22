package net.vonforst.evmap.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat

class VehicleDataScreen(ctx: CarContext) : Screen(ctx), OnCarDataAvailableListener<EnergyLevel> {
    val hardwareMan = ctx.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
    var energyLevel: EnergyLevel? = null

    init {
        hardwareMan.carInfo.addEnergyLevelListener(ContextCompat.getMainExecutor(ctx), this)
    }

    override fun onGetTemplate(): Template {
        val energy = energyLevel ?: return GridTemplate.Builder().setLoading(true).build()

        return GridTemplate.Builder().setSingleList(
            ItemList.Builder().apply {
                energy.batteryPercent.value?.let { percent ->
                    addItem(
                        GridItem.Builder()
                            .setTitle("Battery")
                            .setText("%.1f".format(percent))
                            .build()
                    )
                }
            }.build()
        ).build()
    }

    override fun onCarDataAvailable(data: EnergyLevel) {
        this.energyLevel = energyLevel
        invalidate()
    }
}