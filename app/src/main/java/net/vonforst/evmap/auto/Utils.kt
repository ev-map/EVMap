package net.vonforst.evmap.auto

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.common.CarUnit
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.ftPerMile
import net.vonforst.evmap.getPackageInfoCompat
import net.vonforst.evmap.kmPerMile
import net.vonforst.evmap.shouldUseImperialUnits
import net.vonforst.evmap.ydPerMile
import java.util.Locale
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

fun CarContext.getContentLimit(id: Int) = if (carAppApiLevel >= 2) {
    constraintManager.getContentLimit(id)
} else {
    when (id) {
        ConstraintManager.CONTENT_LIMIT_TYPE_GRID -> 6
        ConstraintManager.CONTENT_LIMIT_TYPE_LIST -> 6
        ConstraintManager.CONTENT_LIMIT_TYPE_PANE -> 4
        ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST -> 6
        ConstraintManager.CONTENT_LIMIT_TYPE_ROUTE_LIST -> 3
        else -> throw IllegalArgumentException("unknown limit ID")
    }
}

val CarContext.isAppDrivenRefreshSupported
    @androidx.car.app.annotations.ExperimentalCarApi
    get() = if (carAppApiLevel >= 6) constraintManager.isAppDrivenRefreshEnabled else false

fun Bitmap.asCarIcon(): CarIcon = CarIcon.Builder(IconCompat.createWithBitmap(this)).build()

val emptyCarIcon: CarIcon by lazy {
    Bitmap.createBitmap(
        1,
        1,
        Bitmap.Config.ARGB_8888
    ).asCarIcon()
}


fun getDefaultDistanceUnit(ctx: Context): Int {
    return if (shouldUseImperialUnits(ctx)) {
        CarUnit.MILE
    } else {
        CarUnit.KILOMETER
    }
}

fun getDefaultSpeedUnit(ctx: Context): Int {
    return if (shouldUseImperialUnits(ctx)) {
        CarUnit.MILES_PER_HOUR
    } else {
        CarUnit.KILOMETERS_PER_HOUR
    }
}

fun formatCarUnitDistance(value: Float?, unit: Int?, ctx: Context): String {
    if (value == null) return ""
    return when (unit ?: getDefaultDistanceUnit(ctx)) {
        // distance units: base unit is meters
        CarUnit.METER -> "%.0f m".format(value)
        CarUnit.KILOMETER -> "%.1f km".format(value / 1000)
        CarUnit.MILLIMETER -> "%.0f mm".format(value * 1000) // whoever uses that...
        CarUnit.MILE -> "%.1f mi".format(value / 1000 / kmPerMile)
        else -> ""
    }
}

fun formatCarUnitSpeed(value: Float?, unit: Int?, ctx: Context): String {
    if (value == null) return ""
    return when (unit ?: getDefaultSpeedUnit(ctx)) {
        // speed units: base unit is meters per second
        CarUnit.METERS_PER_SEC -> "%.0f m/s".format(value)
        CarUnit.KILOMETERS_PER_HOUR -> "%.0f km/h".format(value * 3.6)
        CarUnit.MILES_PER_HOUR -> "%.0f mph".format(value * 3.6 / kmPerMile)
        else -> ""
    }
}

fun roundValueToDistance(value: Double, unit: Int? = null, ctx: Context): Distance {
    // value is in meters
    when (unit ?: getDefaultDistanceUnit(ctx)) {
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
    val info = ctx.packageManager.getPackageInfoCompat("com.google.android.projection.gearhead", 0)
    return info.versionName!!.split(".")
}

fun supportsCarApiLevel3(ctx: CarContext): Boolean {
    if (ctx.carAppApiLevel < CarAppApiLevels.LEVEL_3) return false
    ctx.hostInfo?.let { hostInfo ->
        if (hostInfo.packageName == "com.google.android.projection.gearhead") {
            val version = getAndroidAutoVersion(ctx)
            // Android Auto 6.7 is required. 6.6 reports supporting API Level 3,
            // but crashes when using it. See: https://issuetracker.google.com/issues/199509584
            val major = version[0].toIntOrNull() ?: return false
            val minor = version[1].toIntOrNull() ?: return false
            if (major < 6 || major < 6 && minor < 7) {
                return false
            }
        }
    }
    return true
}

@ExperimentalCarApi
fun openUrl(carContext: CarContext, cas: CarAppService, url: String) {
    val intent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(
                    ContextCompat.getColor(
                        cas,
                        R.color.colorPrimary
                    )
                )
                .build()
        )
        .build().intent
    intent.data = Uri.parse(url)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        cas.startActivity(intent)
        if (BuildConfig.FLAVOR_automotive != "automotive") {
            // only show the toast "opened on phone" if we're running on a phone
            CarToast.makeText(
                carContext,
                R.string.opened_on_phone,
                CarToast.LENGTH_LONG
            ).show()
        }
    } catch (e: ActivityNotFoundException) {
        CarToast.makeText(
            carContext,
            R.string.no_browser_app_found,
            CarToast.LENGTH_LONG
        ).show()
    }
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

class TextMeasurer(ctx: CarContext) {
    val textPaint = TextPaint()

    init {
        textPaint.textSize = ctx.resources.displayMetrics.density * 24
        textPaint.typeface = Typeface.DEFAULT
    }

    fun measureText(text: CharSequence): Float {
        return textPaint.measureText(text, 0, text.length)
    }
}