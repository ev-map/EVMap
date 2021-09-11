package net.vonforst.evmap.auto

import android.graphics.Bitmap
import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import net.vonforst.evmap.api.availability.ChargepointStatus
import java.util.*

fun carAvailabilityColor(status: List<ChargepointStatus>): CarColor {
    val unknown = status.any { it == ChargepointStatus.UNKNOWN }
    val available = status.count { it == ChargepointStatus.AVAILABLE }
    val allFaulted = status.all { it == ChargepointStatus.FAULTED }

    return if (unknown) {
        CarColor.DEFAULT
    } else if (available > 0) {
        CarColor.GREEN
    } else if (allFaulted) {
        CarColor.RED
    } else {
        CarColor.BLUE
    }
}

val CarContext.constraintManager
    get() = getCarService(CarContext.CONSTRAINT_SERVICE) as ConstraintManager

fun Bitmap.asCarIcon(): CarIcon = CarIcon.Builder(IconCompat.createWithBitmap(this)).build()

private const val kmPerMile = 1.609344

fun getDefaultDistanceUnit(): Int {
    return when (Locale.getDefault().country) {
        "US", "GB", "MM", "LR" -> CarUnit.MILE
        else -> CarUnit.KILOMETER
    }
}

fun getDefaultSpeedUnit(): Int {
    return when (Locale.getDefault().country) {
        "US", "GB", "MM", "LR" -> CarUnit.MILES_PER_HOUR
        else -> CarUnit.KILOMETERS_PER_HOUR
    }
}

fun formatCarUnitDistance(value: Float?, unit: Int?): String {
    if (value == null) return ""
    return when (unit ?: getDefaultDistanceUnit()) {
        // distance units: base unit is meters
        CarUnit.METER -> "%.0f m".format(value)
        CarUnit.KILOMETER -> "%.1f km".format(value / 1000)
        CarUnit.MILLIMETER -> "%.0f mm".format(value * 1000) // whoever uses that...
        CarUnit.MILE -> "%.1f mi".format(value / 1000 / kmPerMile)
        else -> ""
    }
}

fun formatCarUnitSpeed(value: Float?, unit: Int?): String {
    if (value == null) return ""
    return when (unit ?: getDefaultSpeedUnit()) {
        // speed units: base unit is meters per second
        CarUnit.METERS_PER_SEC -> "%.0f m/s".format(value)
        CarUnit.KILOMETERS_PER_HOUR -> "%.0f km/h".format(value * 3.6)
        CarUnit.MILES_PER_HOUR -> "%.0f mph".format(value * 3.6 / kmPerMile)
        else -> ""
    }
}