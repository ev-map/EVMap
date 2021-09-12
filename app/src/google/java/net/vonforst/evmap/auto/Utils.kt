package net.vonforst.evmap.auto

import android.content.Context
import android.graphics.Bitmap
import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.versioning.CarAppApiLevels
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

fun getAndroidAutoVersion(ctx: Context): List<String> {
    val info = ctx.packageManager.getPackageInfo("com.google.android.projection.gearhead", 0)
    return info.versionName.split(".")
}

fun supportsCarApiLevel3(ctx: CarContext): Boolean {
    if (ctx.carAppApiLevel < CarAppApiLevels.LEVEL_3) return false
    ctx.hostInfo?.let { hostInfo ->
        if (hostInfo.packageName == "com.google.android.projection.gearhead") {
            val version = getAndroidAutoVersion(ctx)
            // Android Auto 6.7 is required. 6.6 reports supporting API Level 3,
            // but crashes when using it. See: https://issuetracker.google.com/issues/199509584
            if (version[0] < "6" || version[0] == "6" && version[1] < "7") {
                return false
            }
        }
    }
    return true
}