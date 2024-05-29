import android.car.Car
import android.car.VehiclePropertyIds
import android.car.VehicleUnit
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.EvStatus
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.car.app.hardware.info.TollCard
import java.util.concurrent.Executor


val CarContext.patchedCarInfo: CarInfo
    get() = CarInfoWrapper(this)

class CarInfoWrapper(ctx: CarContext) : CarInfo {
    private val wrapped =
        (ctx.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager).carInfo
    private val carPropertyManager = try {
        val car = Car.createCar(ctx)
        car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
    } catch (e: NoClassDefFoundError) {
        null
    }
    private val callbacks = mutableMapOf<OnCarDataAvailableListener<*>, CarPropertyEventCallback>()

    override fun fetchModel(executor: Executor, listener: OnCarDataAvailableListener<Model>) =
        wrapped.fetchModel(executor, listener)

    override fun fetchEnergyProfile(
        executor: Executor,
        listener: OnCarDataAvailableListener<EnergyProfile>
    ) = wrapped.fetchEnergyProfile(executor, listener)

    override fun addTollListener(
        executor: Executor,
        listener: OnCarDataAvailableListener<TollCard>
    ) = wrapped.addTollListener(executor, listener)

    override fun removeTollListener(listener: OnCarDataAvailableListener<TollCard>) =
        wrapped.removeTollListener(listener)

    override fun addEnergyLevelListener(
        executor: Executor,
        listener: OnCarDataAvailableListener<EnergyLevel>
    ) = wrapped.addEnergyLevelListener(executor, listener)

    override fun removeEnergyLevelListener(listener: OnCarDataAvailableListener<EnergyLevel>) =
        wrapped.removeEnergyLevelListener(listener)

    override fun addSpeedListener(executor: Executor, listener: OnCarDataAvailableListener<Speed>) {
        // TODO: This is a emporary workaround until Car App Library 1.7.0 is released - previous versions would crash if the car reported an invalid speed display unit
        carPropertyManager ?: return
        val callback = object : CarPropertyEventCallback {
            private var speedRaw: CarPropertyValue<Float>? = null
            private var speedDisplay: CarPropertyValue<Float>? = null
            private var speedUnit: CarPropertyValue<Int>? = null

            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                when (value?.propertyId) {
                    VehiclePropertyIds.PERF_VEHICLE_SPEED -> speedRaw =
                        value as CarPropertyValue<Float>?

                    VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY -> speedDisplay =
                        value as CarPropertyValue<Float>?

                    VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS -> speedUnit =
                        value as CarPropertyValue<Int>?
                }

                executor.execute {
                    listener.onCarDataAvailable(Speed.Builder().apply {
                        speedRaw?.let {
                            setRawSpeedMetersPerSecond(
                                CarValue(
                                    it.value,
                                    it.timestamp,
                                    if (it.value != null) CarValue.STATUS_SUCCESS else CarValue.STATUS_UNKNOWN
                                )
                            )
                        }
                        speedDisplay?.let {
                            setDisplaySpeedMetersPerSecond(
                                CarValue(
                                    it.value,
                                    it.timestamp,
                                    if (it.value != null) CarValue.STATUS_SUCCESS else CarValue.STATUS_UNKNOWN
                                )
                            )
                        }
                        speedUnit?.let {
                            val unit = when (it.value) {
                                VehicleUnit.METER_PER_SEC -> CarUnit.METERS_PER_SEC
                                VehicleUnit.MILES_PER_HOUR -> CarUnit.MILES_PER_HOUR
                                VehicleUnit.KILOMETERS_PER_HOUR -> CarUnit.KILOMETERS_PER_HOUR
                                else -> null
                            }
                            setSpeedDisplayUnit(
                                CarValue(
                                    unit,
                                    it.timestamp,
                                    if (unit != null) CarValue.STATUS_SUCCESS else CarValue.STATUS_UNKNOWN
                                )
                            )
                        }
                    }.build())
                }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                listener.onCarDataAvailable(
                    Speed.Builder()
                        .setRawSpeedMetersPerSecond(CarValue(null, 0, CarValue.STATUS_UNKNOWN))
                        .setDisplaySpeedMetersPerSecond(CarValue(null, 0, CarValue.STATUS_UNKNOWN))
                        .setSpeedDisplayUnit(CarValue(null, 0, CarValue.STATUS_UNKNOWN))
                        .build()
                )
            }
        }
        carPropertyManager.registerCallback(
            callback,
            VehiclePropertyIds.PERF_VEHICLE_SPEED,
            CarPropertyManager.SENSOR_RATE_NORMAL
        )
        carPropertyManager.registerCallback(
            callback,
            VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
            CarPropertyManager.SENSOR_RATE_NORMAL
        )
        carPropertyManager.registerCallback(
            callback,
            VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS,
            CarPropertyManager.SENSOR_RATE_NORMAL
        )
    }

    override fun removeSpeedListener(listener: OnCarDataAvailableListener<Speed>) {
        val callback = callbacks[listener] ?: return
        carPropertyManager?.unregisterCallback(callback)
    }

    override fun addMileageListener(
        executor: Executor,
        listener: OnCarDataAvailableListener<Mileage>
    ) = wrapped.addMileageListener(executor, listener)

    override fun removeMileageListener(listener: OnCarDataAvailableListener<Mileage>) =
        wrapped.removeMileageListener(listener)

    @OptIn(ExperimentalCarApi::class)
    override fun addEvStatusListener(
        executor: Executor,
        listener: OnCarDataAvailableListener<EvStatus>
    ) = wrapped.addEvStatusListener(executor, listener)

    @OptIn(ExperimentalCarApi::class)
    override fun removeEvStatusListener(listener: OnCarDataAvailableListener<EvStatus>) =
        wrapped.removeEvStatusListener(listener)
}