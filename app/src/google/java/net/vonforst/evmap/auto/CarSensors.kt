package net.vonforst.evmap.auto

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.*
import androidx.car.app.hardware.info.CarSensors.UpdateRate
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.storage.PreferenceDataSource
import java.util.concurrent.Executor

/**
 * CarSensors is not yet implemented for Android Automotive OS
 * (see docs at https://developer.android.com/reference/androidx/car/app/hardware/info/CarSensors)
 * so we provide our own implementation based on SensorManager APIs.
 */
val CarContext.patchedCarSensors: CarSensors
    get() = if (BuildConfig.FLAVOR_automotive != "automotive") {
        (this.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager).carSensors
    } else {
        CarSensorsWrapper(this)
    }

class CarSensorsWrapper(carContext: CarContext) :
    CarSensors {
    private val sensorManager = carContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val compassListeners: MutableMap<OnCarDataAvailableListener<Compass>, SensorEventListener> =
        mutableMapOf()
    private val isDeveloper = PreferenceDataSource(carContext).developerModeEnabled

    override fun addAccelerometerListener(
        rate: Int,
        executor: Executor,
        listener: OnCarDataAvailableListener<Accelerometer>
    ) {
        TODO("Not yet implemented")
    }

    override fun removeAccelerometerListener(listener: OnCarDataAvailableListener<Accelerometer>) {
        TODO("Not yet implemented")
    }

    override fun addGyroscopeListener(
        rate: Int,
        executor: Executor,
        listener: OnCarDataAvailableListener<Gyroscope>
    ) {
        TODO("Not yet implemented")
    }

    override fun removeGyroscopeListener(listener: OnCarDataAvailableListener<Gyroscope>) {
        TODO("Not yet implemented")
    }

    override fun addCompassListener(
        rate: Int,
        executor: Executor,
        listener: OnCarDataAvailableListener<Compass>
    ) {
        val rotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gameRotVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (rotVectorSensor != null && isDeveloper) {
            // experimental
            addCompassListenerRotationVector(rate, executor, listener, rotVectorSensor)
        } else if (magSensor != null) {
            addCompassListenerMagneticField(rate, executor, listener, magSensor, accSensor)
        } else if (gameRotVectorSensor != null && isDeveloper) {
            // experimental
            addCompassListenerRotationVector(rate, executor, listener, gameRotVectorSensor)
        }
        executor.execute {
            listener.onCarDataAvailable(Compass(CarValue(null, 0, CarValue.STATUS_UNAVAILABLE)))
        }
    }

    private fun addCompassListenerRotationVector(
        rate: Int,
        executor: Executor,
        listener: OnCarDataAvailableListener<Compass>,
        sensor: Sensor
    ) {
        val sensorListener = object : SensorEventListener {
            val rotMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                val rotVector = event.values ?: return
                SensorManager.getRotationMatrixFromVector(rotMatrix, rotVector)
                SensorManager.getOrientation(rotMatrix, orientation)
                val compassDegrees = orientation.map { Math.toDegrees(it.toDouble()).toFloat() }

                executor.execute {
                    listener.onCarDataAvailable(
                        Compass(
                            CarValue(
                                compassDegrees,
                                event.timestamp,
                                CarValue.STATUS_SUCCESS
                            )
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }

        }
        compassListeners[listener] = sensorListener
        sensorManager.registerListener(sensorListener, sensor, mapRate(rate))
    }

    /**
     * Compass listener implementation based on magnetic field sensor, if available.
     */
    private fun addCompassListenerMagneticField(
        rate: Int,
        executor: Executor,
        listener: OnCarDataAvailableListener<Compass>,
        magSensor: Sensor,
        accSensor: Sensor?
    ) {
        val sensorListener = object : SensorEventListener {
            var magValues: FloatArray? = null

            // AAOS cars may not provide an acceleration sensor, so we assume acceleration based on
            // Earth's gravity. May not be correct when driving on other planets.
            var accValues = floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH)
            val rotMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor) {
                    magSensor -> magValues = event.values
                    accSensor -> accValues = event.values
                }
                if (magValues == null) return

                SensorManager.getRotationMatrix(rotMatrix, null, accValues, magValues)
                SensorManager.getOrientation(rotMatrix, orientation)
                val compassDegrees = orientation.map { Math.toDegrees(it.toDouble()).toFloat() }
                executor.execute {
                    listener.onCarDataAvailable(
                        Compass(
                            CarValue(
                                compassDegrees,
                                event.timestamp,
                                CarValue.STATUS_SUCCESS
                            )
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            }

        }
        compassListeners[listener] = sensorListener
        sensorManager.registerListener(sensorListener, magSensor, mapRate(rate))
        accSensor?.let { sensorManager.registerListener(sensorListener, it, mapRate(rate)) }
    }

    private fun mapRate(@UpdateRate rate: Int): Int {
        return when (rate) {
            CarSensors.UPDATE_RATE_NORMAL -> SensorManager.SENSOR_DELAY_NORMAL
            CarSensors.UPDATE_RATE_UI -> SensorManager.SENSOR_DELAY_UI
            CarSensors.UPDATE_RATE_FASTEST -> SensorManager.SENSOR_DELAY_FASTEST
            else -> throw IllegalArgumentException()
        }
    }

    override fun removeCompassListener(listener: OnCarDataAvailableListener<Compass>) {
        compassListeners[listener]?.let {
            sensorManager.unregisterListener(it)
        }
    }

    override fun addCarHardwareLocationListener(
        rate: Int,
        executor: Executor,
        listener: OnCarDataAvailableListener<CarHardwareLocation>
    ) {
        TODO("Not yet implemented")
    }

    override fun removeCarHardwareLocationListener(listener: OnCarDataAvailableListener<CarHardwareLocation>) {
        TODO("Not yet implemented")
    }
}
