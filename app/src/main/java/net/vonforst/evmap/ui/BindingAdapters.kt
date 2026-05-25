package net.vonforst.evmap.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.kmPerMile
import net.vonforst.evmap.meterPerFt
import net.vonforst.evmap.shouldUseImperialUnits
import java.time.Instant

fun goneUnless(view: View, visible: Boolean) {
    view.visibility = if (visible) View.VISIBLE else View.GONE
}

fun invisibleUnless(view: View, visible: Boolean) {
    view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
}

fun isFabActive(view: FloatingActionButton, isColored: Boolean) {
    view.imageTintList = activeTint(view.context, isColored)
}

private fun activeTint(context: Context, isColored: Boolean): ColorStateList {
    val colorAttr = if (isColored) {
        androidx.appcompat.R.attr.colorPrimary
    } else {
        androidx.appcompat.R.attr.colorControlNormal
    }
    return context.obtainStyledAttributes(intArrayOf(colorAttr)).use {
        ColorStateList.valueOf(it.getColor(0, 0))
    }
}

fun applySelectableItemBackground(view: View, apply: Boolean) {
    if (apply) {
        view.context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.selectableItemBackground))
            .use {
                view.background = it.getDrawable(0)
            }
    } else {
        view.background = null
    }
}

fun setLinkify(textView: TextView, linkifyMask: Int, text: CharSequence?) {
    textView.text = text
    if (linkifyMask != 0) {
        Linkify.addLinks(textView, linkifyMask)
        textView.movementMethod = LinkMovementMethod.getInstance()
    } else {
        textView.movementMethod = null
    }
}

fun availabilityColor(status: List<ChargepointStatus>?, context: Context): Int =
    if (status != null) {
    val unknown = status.any { it == ChargepointStatus.UNKNOWN }
    val available = status.count { it == ChargepointStatus.AVAILABLE }
    val allFaulted = status.all { it == ChargepointStatus.FAULTED }

        when {
            unknown -> ContextCompat.getColor(context, R.color.unknown)
            available > 0 -> ContextCompat.getColor(context, R.color.available)
            allFaulted -> ContextCompat.getColor(context, R.color.unavailable)
            else -> ContextCompat.getColor(context, R.color.charging)
    }
} else {
        context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorControlNormal))
            .use {
                it.getColor(0, 0)
            }
}

fun availabilityColor(status: ChargepointStatus?, context: Context): Int = when (status) {
    ChargepointStatus.UNKNOWN -> ContextCompat.getColor(context, R.color.unknown)
    ChargepointStatus.AVAILABLE -> ContextCompat.getColor(context, R.color.available)
    ChargepointStatus.FAULTED -> ContextCompat.getColor(context, R.color.unavailable)
    ChargepointStatus.OCCUPIED, ChargepointStatus.CHARGING -> ContextCompat.getColor(
        context,
        R.color.charging
    )

    null -> context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorControlNormal))
        .use {
            it.getColor(0, 0)
    }
}

fun availabilityText(status: List<ChargepointStatus>?): String? {
    if (status == null) return null
    val total = status.size
    val unknown = status.count { it == ChargepointStatus.UNKNOWN }
    val available = status.count { it == ChargepointStatus.AVAILABLE }
    return if (unknown > 0) {
        if (unknown == total) "?" else "$available?"
    } else {
        available.toString()
    }
}

fun availabilityText(status: ChargepointStatus?, lastChange: Instant?, context: Context): String? {
    if (status == null) return null
    val statusText = when (status) {
        ChargepointStatus.UNKNOWN -> context.getString(R.string.status_unknown)
        ChargepointStatus.AVAILABLE -> context.getString(R.string.status_available)
        ChargepointStatus.CHARGING -> context.getString(R.string.status_charging)
        ChargepointStatus.OCCUPIED -> context.getString(R.string.status_occupied)
        ChargepointStatus.FAULTED -> context.getString(R.string.status_faulted)
    }
    return if (lastChange != null) {
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            lastChange.toEpochMilli(),
            Instant.now().toEpochMilli(),
            0
        ).toString()
        context.getString(R.string.status_since, statusText, relativeTime)
    } else {
        statusText
    }
}

fun flatten(values: Iterable<Iterable<ChargepointStatus>>?): List<ChargepointStatus>? {
    return values?.flatten()
}

fun currency(currency: String): String = when (currency) {
    "EUR" -> "€"
    "USD" -> "$"
    "DKK", "SEK", "NOK" -> "kr."
    "PLN" -> "zł"
    "CHF" -> "Fr. "
    "CZK" -> "Kč"
    "GBP" -> "£"
    "HRK" -> "kn"
    "HUF" -> "Ft"
    "ISK" -> "kr"
    else -> currency
}

fun distance(meters: Number?, ctx: Context): String? {
    if (meters == null) return null
    return if (shouldUseImperialUnits(ctx)) {
        val ft = meters.toDouble() / meterPerFt
        val mi = meters.toDouble() / 1e3 / kmPerMile
        when {
            ft < 1000 -> "%.0f ft".format(ft)
            mi < 10 -> "%.1f mi".format(mi)
            else -> "%.0f mi".format(mi)
        }
    } else {
        val km = meters.toDouble() / 1e3
        when {
            km < 1 -> "%.0f m".format(meters.toDouble())
            km < 10 -> "%.1f km".format(km)
            else -> "%.0f km".format(km)
        }
    }
}

