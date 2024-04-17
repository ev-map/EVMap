package net.vonforst.evmap

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.TextUtils
import android.text.style.StyleSpan
import net.vonforst.evmap.storage.PreferenceDataSource
import java.util.Locale

fun Bundle.optDouble(name: String): Double? {
    if (!this.containsKey(name)) return null

    val dbl = this.getDouble(name, Double.NaN)
    return if (dbl.isNaN()) null else dbl
}

fun Bundle.optLong(name: String): Long? {
    if (!this.containsKey(name)) return null

    val lng = this.getLong(name, Long.MIN_VALUE)
    return if (lng == Long.MIN_VALUE) null else lng
}

fun <T> Iterable<T>.joinToSpannedString(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null
): CharSequence {
    return SpannedString(
        joinTo(
            SpannableStringBuilder(),
            separator,
            prefix,
            postfix,
            limit,
            truncated,
            transform
        )
    )
}

operator fun CharSequence.plus(other: CharSequence): CharSequence {
    return TextUtils.concat(this, other)
}

fun String.bold(): CharSequence {
    return SpannableString(this).apply {
        setSpan(
            StyleSpan(Typeface.BOLD), 0, this.length,
            Spannable.SPAN_INCLUSIVE_EXCLUSIVE
        )
    }
}

fun <T> Collection<Iterable<T>>.cartesianProduct(): Set<Set<T>> =
    /**
    Returns all possible combinations of entries of a list
     */
    if (isEmpty()) emptySet()
    else drop(1).fold(first().map(::setOf)) { acc, iterable ->
        acc.flatMap { list -> iterable.map(list::plus) }
    }.toSet()


fun max(a: Int?, b: Int?): Int? {
    /**
     * Returns the maximum of two values of both are non-null,
     * otherwise the non-null value or null
     */
    return if (a != null && b != null) {
        kotlin.math.max(a, b)
    } else {
        a ?: b
    }
}

fun <T> List<T>.containsAny(vararg values: T) = values.any { this.contains(it) }

fun Context.isDarkMode() =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

const val kmPerMile = 1.609344
const val meterPerFt = 0.3048
const val ftPerMile = 5280
const val ydPerMile = 1760

fun shouldUseImperialUnits(ctx: Context): Boolean {
    val prefs = PreferenceDataSource(ctx)
    return when (prefs.units) {
        "metric" -> false
        "imperial" -> true
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when (LocaleData.getMeasurementSystem(ULocale.getDefault())) {
                LocaleData.MeasurementSystem.US, LocaleData.MeasurementSystem.UK -> true
                LocaleData.MeasurementSystem.SI -> false
                else -> false
            }
        } else {
            return Locale.getDefault().country in listOf("US", "GB", "MM", "LR")
        }
    }

}

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
    }


fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int = 0): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getApplicationInfo(packageName, flags)
    }


fun PackageManager.isAppInstalled(packageName: String): Boolean {
    return try {
        getApplicationInfoCompat(packageName, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}