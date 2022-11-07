package net.vonforst.evmap.auto

import android.content.Context
import android.graphics.Bitmap
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.model.*
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.graphics.drawable.IconCompat
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import java.util.*
import kotlin.math.roundToInt

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

val emptyCarIcon: CarIcon by lazy {
    Bitmap.createBitmap(
        1,
        1,
        Bitmap.Config.ARGB_8888
    ).asCarIcon()
}

private const val kmPerMile = 1.609344
private const val ftPerMile = 5280
private const val ydPerMile = 1760

fun getDefaultDistanceUnit(): Int {
    return if (usesImperialUnits(Locale.getDefault())) {
        CarUnit.MILE
    } else {
        CarUnit.KILOMETER
    }
}

fun usesImperialUnits(locale: Locale): Boolean {
    return locale.country in listOf("US", "GB", "MM", "LR")
            || locale.country == "" && locale.language == "en"
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

fun roundValueToDistance(value: Double, unit: Int? = null): Distance {
    // value is in meters
    when (unit ?: getDefaultDistanceUnit()) {
        CarUnit.MILE -> {
            // imperial system
            val miles = value / 1000 / kmPerMile
            val yards = miles * ydPerMile
            val feet = miles * ftPerMile

            return when (miles) {
                in 0.0..0.1 -> if (Locale.getDefault().country == "UK") {
                    Distance.create(roundToMultipleOf(yards, 10.0), Distance.UNIT_YARDS)
                } else {
                    Distance.create(roundToMultipleOf(feet, 10.0), Distance.UNIT_FEET)
                }
                in 0.1..10.0 -> Distance.create(
                    roundToMultipleOf(miles, 0.1),
                    Distance.UNIT_MILES_P1
                )
                else -> Distance.create(roundToMultipleOf(miles, 1.0), Distance.UNIT_MILES)
            }
        }
        else -> {
            // metric system
            return when (value) {
                in 0.0..999.0 -> Distance.create(
                    roundToMultipleOf(value, 10.0),
                    Distance.UNIT_METERS
                )
                in 1000.0..10000.0 -> Distance.create(
                    roundToMultipleOf(value / 1000, 0.1),
                    Distance.UNIT_KILOMETERS_P1
                )
                else -> Distance.create(
                    roundToMultipleOf(value / 1000, 1.0),
                    Distance.UNIT_KILOMETERS
                )
            }
        }
    }
}

private fun roundToMultipleOf(num: Double, step: Double): Double {
    return (num / step).roundToInt() * step
}

/**
 * Paginates data based on specific limits for each page.
 * If the data fits on a single page, this page can have a maximum size nSingle. Otherwise, the
 * first page has maximum nFirst items, the last page nLast items, and all intermediate pages nOther
 * items.
 */
fun <T> List<T>.paginate(nSingle: Int, nFirst: Int, nOther: Int, nLast: Int): List<List<T>> {
    if (nOther > nLast) {
        throw IllegalArgumentException("nLast has to be larger than or equal to nOther")
    }
    return if (size <= nSingle) {
        listOf(this)
    } else {
        val result = mutableListOf<List<T>>()
        var i = 0
        var page = 0
        while (true) {
            val remaining = size - i
            if (page == 0) {
                result.add(subList(i, i + nFirst))
                i += nFirst
            } else if (remaining <= nLast) {
                result.add(subList(i, size))
                break
            } else {
                result.add(subList(i, i + nOther))
                i += nOther
            }
            page++
        }
        result
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

class DummyReturnScreen(ctx: CarContext) : Screen(ctx) {
    /*
    Dummy screen to get around template refresh limitations.
    It immediately pops back to the previous screen.
     */
    override fun onGetTemplate(): Template {
        screenManager.pop()
        return MessageTemplate.Builder(carContext.getString(R.string.loading)).setLoading(true)
            .build()
    }

}