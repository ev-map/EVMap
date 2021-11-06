package net.vonforst.evmap.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableString
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
import net.vonforst.evmap.*
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.iconForPlugType
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
                R.attr.colorPrimary
            } else {
                R.attr.colorControlNormal
            }
        )
    )
    val valueOf = ColorStateList.valueOf(color.getColor(0, 0))
    return valueOf
}

@BindingAdapter("data")
fun <T> setRecyclerViewData(recyclerView: RecyclerView, items: List<T>?) {
    if (recyclerView.adapter is ListAdapter<*, *>) {
        (recyclerView.adapter as ListAdapter<T, *>).submitList(items)
    }
}

@BindingAdapter("data")
fun <T> setRecyclerViewData(recyclerView: ViewPager2, items: List<T>?) {
    if (recyclerView.adapter is ListAdapter<*, *>) {
        (recyclerView.adapter as ListAdapter<T, *>).submitList(items)
    }
}

@BindingAdapter("connectorIcon")
fun getConnectorItem(view: ImageView, type: String) {
    view.setImageResource(iconForPlugType(type))
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

@BindingAdapter("textColorAvailability")
fun setTextColorAvailability(view: TextView, available: List<ChargepointStatus>?) {
    view.setTextColor(availabilityColor(available, view.context))
}

@BindingAdapter("backgroundTintAvailability")
fun setBackgroundTintAvailability(view: View, available: List<ChargepointStatus>?) {
    view.backgroundTintList = ColorStateList.valueOf(availabilityColor(available, view.context))
}

@BindingAdapter("selectableItemBackground")
fun applySelectableItemBackground(view: View, apply: Boolean) {
    if (apply) {
        view.context.obtainStyledAttributes(intArrayOf(R.attr.selectableItemBackground)).use {
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
 * remove spans correctly. So we implement a new version that manually removes the spans.
 */
@BindingAdapter("linkify")
fun setLinkify(textView: TextView, oldValue: Int, newValue: Int) {
    if (oldValue == newValue) return

    textView.autoLinkMask = newValue
    textView.linksClickable = newValue != 0

    // remove spans
    val text = textView.text
    if (newValue == 0 && text != null && text is SpannableString) {
        text.getSpans(0, text.length, Any::class.java).forEach {
            text.removeSpan(it)
        }
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
    val ta = context.theme.obtainStyledAttributes(intArrayOf(R.attr.colorControlNormal))
    ta.getColor(0, 0)
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
        "ISK" -> "Kr"
        else -> currency
    }
}

fun time(value: Int): String {
    val h = floor(value.toDouble() / 60).toInt();
    val min = ceil(value.toDouble() % 60).toInt();
    return if (h == 0 && min > 0) "$min min";
    else "%d:%02d h".format(h, min);
}

fun distance(meters: Number?): String? {
    if (meters == null) return null
    if (shouldUseImperialUnits()) {
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

@InverseBindingAdapter(attribute = "app:values")
fun getRangeSliderValue(slider: RangeSlider) = slider.values

@BindingAdapter("app:valuesAttrChanged")
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

@BindingAdapter("app:tint")
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
            context.obtainStyledAttributes(intArrayOf(R.attr.selectableItemBackground)).use {
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

    val newRed = ((red - (1 - targetAlpha) * 255) / targetAlpha).roundToInt()
    val newGreen = ((green - (1 - targetAlpha) * 255) / targetAlpha).roundToInt()
    val newBlue = ((blue - (1 - targetAlpha) * 255) / targetAlpha).roundToInt()

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