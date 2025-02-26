package net.vonforst.evmap.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.text.HtmlCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.RangeSlider
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.iconForPlugType
import net.vonforst.evmap.isDarkMode
import net.vonforst.evmap.kmPerMile
import net.vonforst.evmap.meterPerFt
import net.vonforst.evmap.shouldUseImperialUnits
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt


@BindingAdapter("goneUnless")
fun goneUnless(view: View, visible: Boolean) {
    view.visibility = if (visible) View.VISIBLE else View.GONE
}

@BindingAdapter("goneUnlessAnimated")
fun goneUnlessAnimated(view: View, oldValue: Boolean, newValue: Boolean) {
    if (oldValue == newValue) return

    view.animate().cancel()
    if (newValue) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.animate().alpha(1f).withEndAction {
            view.alpha = 1f
        }
    } else {
        view.animate().alpha(0f).withEndAction {
            view.alpha = 1f
            view.visibility = View.GONE
        }
    }
}

@BindingAdapter("invisibleUnless")
fun invisibleUnless(view: View, visible: Boolean) {
    view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
}

@BindingAdapter("invisibleUnlessAnimated")
fun invisibleUnlessAnimated(view: View, oldValue: Boolean, newValue: Boolean) {
    if (oldValue == newValue) {
        if (!newValue && view.visibility == View.VISIBLE && view.alpha == 1f) {
            // view is initially invisible
            view.visibility = View.INVISIBLE
        } else {
            return
        }
    }

    view.animate().cancel()
    if (newValue) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.animate().alpha(1f).withEndAction {
            view.alpha = 1f
        }
    } else {
        view.animate().alpha(0f).withEndAction {
            view.alpha = 1f
            view.visibility = View.INVISIBLE
        }
    }
}

@BindingAdapter("isFabActive")
fun isFabActive(view: FloatingActionButton, isColored: Boolean) {
    view.imageTintList = activeTint(view.context, isColored)
}

@BindingAdapter("backgroundTintActive")
fun backgroundTintActive(view: View, isColored: Boolean) {
    view.backgroundTintList = activeTint(view.context, isColored)
}

@BindingAdapter("imageTintActive")
fun imageTintActive(view: ImageView, isColored: Boolean) {
    view.imageTintList = activeTint(view.context, isColored)
}

private fun activeTint(
    context: Context,
    isColored: Boolean
): ColorStateList {
    val color = context.theme.obtainStyledAttributes(
        intArrayOf(
            if (isColored) {
                androidx.appcompat.R.attr.colorPrimary
            } else {
                androidx.appcompat.R.attr.colorControlNormal
            }
        )
    )
    val valueOf = ColorStateList.valueOf(color.getColor(0, 0))
    return valueOf
}

@BindingAdapter("data")
@Suppress("UNCHECKED_CAST")
fun <T> setRecyclerViewData(recyclerView: RecyclerView, items: List<T>?) {
    if (recyclerView.adapter is ListAdapter<*, *>) {
        (recyclerView.adapter as ListAdapter<T, *>).submitList(items)
    }
}

@BindingAdapter("data")
@Suppress("UNCHECKED_CAST")
fun <T> setRecyclerViewData(recyclerView: ViewPager2, items: List<T>?) {
    if (recyclerView.adapter is ListAdapter<*, *>) {
        (recyclerView.adapter as ListAdapter<T, *>).submitList(items)
    }
}

@BindingAdapter("connectorIcon")
fun getConnectorItem(view: ImageView, type: String?) {
    view.setImageResource(type?.let { iconForPlugType(it) } ?: 0)
}

@BindingAdapter("srcCompat")
fun setImageResource(imageView: ImageView, resource: Int) {
    imageView.setImageResource(resource)
}

@BindingAdapter("android:contentDescription")
fun setContentDescriptionResource(imageView: ImageView, resource: Int) {
    imageView.contentDescription = imageView.context.getString(resource)
}

@BindingAdapter("tintAvailability")
fun setImageTintAvailability(view: ImageView, available: List<ChargepointStatus>?) {
    view.imageTintList = ColorStateList.valueOf(availabilityColor(available, view.context))
}

@BindingAdapter("tintAvailability")
fun setImageTintAvailability(view: ImageView, available: ChargepointStatus?) {
    view.imageTintList = ColorStateList.valueOf(availabilityColor(available, view.context))
}

@BindingAdapter("textColorAvailability")
fun setTextColorAvailability(view: TextView, available: List<ChargepointStatus>?) {
    view.setTextColor(availabilityColor(available, view.context))
}

@BindingAdapter("textColorAvailability")
fun setTextColorAvailability(view: TextView, available: ChargepointStatus?) {
    view.setTextColor(availabilityColor(available, view.context))
}

@BindingAdapter("backgroundTintAvailability")
fun setBackgroundTintAvailability(view: View, available: List<ChargepointStatus>?) {
    view.backgroundTintList = ColorStateList.valueOf(availabilityColor(available, view.context))
}

@BindingAdapter("selectableItemBackground")
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

@BindingAdapter("htmlText")
fun setHtmlTextValue(textView: TextView, htmlText: String?) {
    if (htmlText == null) {
        textView.text = null
    } else {
        textView.text = HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}

@BindingAdapter("android:layout_marginTop")
fun setTopMargin(view: View, topMargin: Float) {
    val layoutParams = view.layoutParams as MarginLayoutParams
    layoutParams.setMargins(
        layoutParams.leftMargin, topMargin.roundToInt(),
        layoutParams.rightMargin, layoutParams.bottomMargin
    )
    view.layoutParams = layoutParams
}

/**
 * Linkify is already possible using the autoLink and linksClickable attributes, but this does not
 * remove spans correctly after autoLink is set to false.
 * So we implement a new version that manually uses Linkify to create links if necessary.
 */
@BindingAdapter(value = ["linkify", "android:text"])
fun setLinkify(
    textView: TextView,
    oldLinkify: Int,
    oldText: CharSequence?,
    newLinkify: Int,
    newText: CharSequence?
) {
    if (oldLinkify == newLinkify && oldText == newText) return

    textView.text = newText
    if (newLinkify != 0) {
        Linkify.addLinks(textView, newLinkify)
        textView.movementMethod = LinkMovementMethod.getInstance()
    } else {
        textView.movementMethod = null
    }
}

@BindingAdapter("chargepriceTagColor")
fun setChargepriceTagColor(view: TextView, kind: String) {
    view.backgroundTintList = ColorStateList.valueOf(
        ContextCompat.getColor(
            view.context,
            when (kind) {
                "star" -> R.color.chargeprice_star
                "alert" -> R.color.chargeprice_alert
                "info" -> R.color.chargeprice_info
                "lock" -> R.color.chargeprice_lock
                else -> R.color.chip_background
            }
        )
    )
}

@BindingAdapter("chargepriceTagIcon")
fun setChargepriceTagIcon(view: TextView, kind: String) {
    view.setCompoundDrawablesRelativeWithIntrinsicBounds(
        when (kind) {
            "star" -> R.drawable.ic_chargeprice_star
            "alert" -> R.drawable.ic_chargeprice_alert
            "info" -> R.drawable.ic_chargeprice_info
            "lock" -> R.drawable.ic_chargeprice_lock
            else -> 0
        }, 0, 0, 0
    )
}

private fun availabilityColor(
    status: List<ChargepointStatus>?,
    context: Context
): Int = if (status != null) {
    val unknown = status.any { it == ChargepointStatus.UNKNOWN }
    val available = status.count { it == ChargepointStatus.AVAILABLE }
    val allFaulted = status.all { it == ChargepointStatus.FAULTED }

    if (unknown) {
        ContextCompat.getColor(context, R.color.unknown)
    } else if (available > 0) {
        ContextCompat.getColor(context, R.color.available)
    } else if (allFaulted) {
        ContextCompat.getColor(context, R.color.unavailable)
    } else {
        ContextCompat.getColor(context, R.color.charging)
    }
} else {
    val ta =
        context.theme.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorControlNormal))
    ta.getColor(0, 0)
}

private fun availabilityColor(
    status: ChargepointStatus?,
    context: Context
): Int = when (status) {
    ChargepointStatus.UNKNOWN -> ContextCompat.getColor(context, R.color.unknown)
    ChargepointStatus.AVAILABLE -> ContextCompat.getColor(context, R.color.available)
    ChargepointStatus.FAULTED -> ContextCompat.getColor(context, R.color.unavailable)
    ChargepointStatus.OCCUPIED, ChargepointStatus.CHARGING -> ContextCompat.getColor(
        context,
        R.color.charging
    )

    null -> {
        val ta =
            context.theme.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.colorControlNormal))
        ta.getColor(0, 0)
    }
}

fun availabilityText(status: List<ChargepointStatus>?): String? {
    if (status == null) return null

    val total = status.size
    val unknown = status.count { it == ChargepointStatus.UNKNOWN }
    val available = status.count { it == ChargepointStatus.AVAILABLE }

    return if (unknown > 0) {
        if (unknown == total) "?" else "$available?"
    } else available.toString()
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
        return context.getString(R.string.status_since, statusText, relativeTime)
    } else {
        statusText
    }
}

fun flatten(it: Iterable<Iterable<ChargepointStatus>>?): List<ChargepointStatus>? {
    return it?.flatten()
}

fun currency(currency: String): String {
    // shorthands for currencies
    return when (currency) {
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
}

fun time(value: Int): String {
    val h = floor(value.toDouble() / 60).toInt()
    val min = ceil(value.toDouble() % 60).toInt()
    return if (h == 0 && min > 0) "$min min"
    else "%d:%02d h".format(h, min)
}

fun distance(meters: Number?, ctx: Context): String? {
    if (meters == null) return null
    if (shouldUseImperialUnits(ctx)) {
        val ft = meters.toDouble() / meterPerFt
        val mi = meters.toDouble() / 1e3 / kmPerMile
        return when {
            ft < 1000 -> "%.0f ft".format(ft)
            mi < 10 -> "%.1f mi".format(mi)
            else -> "%.0f mi".format(mi)
        }
    } else {
        val km = meters.toDouble() / 1e3
        return when {
            km < 1 -> "%.0f m".format(meters.toDouble())
            km < 10 -> "%.1f km".format(km)
            else -> "%.0f km".format(km)
        }
    }
}

@InverseBindingAdapter(attribute = "values")
fun getRangeSliderValue(slider: RangeSlider) = slider.values

@BindingAdapter("valuesAttrChanged")
fun setRangeSliderListeners(slider: RangeSlider, attrChange: InverseBindingListener) {
    slider.addOnChangeListener { _, _, _ ->
        attrChange.onChange()
    }
}

@ColorInt
fun colorEnabled(ctx: Context, enabled: Boolean): Int {
    val attr = if (enabled) {
        android.R.attr.textColorSecondary
    } else {
        android.R.attr.textColorHint
    }
    val typedValue = ctx.obtainStyledAttributes(intArrayOf(attr))
    val color = typedValue.getColor(0, 0)
    typedValue.recycle()
    return color
}

@BindingAdapter("tint")
fun setImageTintList(view: ImageView, @ColorInt color: Int) {
    view.imageTintList = ColorStateList.valueOf(color)
}

fun tariffBackground(context: Context, myTariff: Boolean, brandingColor: String?): Drawable? {
    when {
        myTariff -> {
            return ContextCompat.getDrawable(context, R.drawable.my_tariff_background)
        }
        brandingColor != null -> {
            val drawable = ContextCompat.getDrawable(context, R.drawable.branded_tariff_background)
            val color = colorToTransparent(Color.parseColor(brandingColor))
            (drawable as LayerDrawable).setDrawableByLayerId(
                R.id.background, ColorDrawable(
                    color
                )
            )
            return drawable
        }
        else -> {
            context.obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.selectableItemBackground))
                .use {
                    return it.getDrawable(0)
                }
        }
    }
}

fun isDarkMode(context: Context) = context.isDarkMode()

/**
 * Converts an opaque color to a transparent color, assuming it was on a white background
 * with a certain opacity targetAlpha.
 */
private fun colorToTransparent(color: Int, targetAlpha: Float = 31f / 255): Int {
    if (Color.alpha(color) != 255) return color

    val red = Color.red(color)
    val green = Color.green(color)
    val blue = Color.blue(color)

    val newRed = kotlin.math.max(((red - (1 - targetAlpha) * 255) / targetAlpha).roundToInt(), 0)
    val newGreen =
        kotlin.math.max(((green - (1 - targetAlpha) * 255) / targetAlpha).roundToInt(), 0)
    val newBlue = kotlin.math.max(((blue - (1 - targetAlpha) * 255) / targetAlpha).roundToInt(), 0)

    return Color.argb((targetAlpha * 255).roundToInt(), newRed, newGreen, newBlue)
}

@BindingAdapter("imageUrl")
fun loadImage(view: ImageView, url: String?) {
    if (url != null) {
        view.load(url)
    } else {
        view.setImageDrawable(null)
    }
}

@BindingAdapter("tooltipTextCompat")
fun setTooltipTextCompat(view: View, text: String) {
    TooltipCompat.setTooltipText(view, text)
}

@BindingAdapter("tintNullable")
fun setImageTint(view: ImageView, @ColorInt tint: Int?) {
    if (tint != null) {
        view.imageTintList = ColorStateList.valueOf(tint)
    } else {
        view.imageTintList = null
    }
}

@BindingAdapter("isPercentage")
fun setIsPercentage(view: BarGraphView, value: Boolean) {
    view.isPercentage = value
}