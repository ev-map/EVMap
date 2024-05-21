import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.CarInfo

val CarContext.patchedCarInfo: CarInfo
    get() = (this.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager).carInfo