package net.vonforst.evmap.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.text.HtmlCompat
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.goingelectric.Chargepoint
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

@BindingAdapter("isFabActive")
fun isFabActive(view: FloatingActionButton, isColored: Boolean) {
    val color = view.context.theme.obtainStyledAttributes(
        intArrayOf(
            if (isColored) {
                R.attr.colorAccent
            } else {
                R.attr.colorControlNormal
            }
        )
    )
    view.imageTintList = ColorStateList.valueOf(color.getColor(0, 0))
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
    view.setImageResource(
        when (type) {
            Chargepoint.CCS -> R.drawable.ic_connector_ccs
            Chargepoint.CHADEMO -> R.drawable.ic_connector_chademo
            Chargepoint.SCHUKO -> R.drawable.ic_connector_schuko
            Chargepoint.SUPERCHARGER -> R.drawable.ic_connector_supercharger
            Chargepoint.TYPE_2 -> R.drawable.ic_connector_typ2
            Chargepoint.CEE_BLAU -> R.drawable.ic_connector_cee_blau
            Chargepoint.CEE_ROT -> R.drawable.ic_connector_cee_rot
            Chargepoint.TYPE_1 -> R.drawable.ic_connector_typ1
            // TODO: add other connectors
            else -> 0
        }
    )
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

private fun availabilityColor(
    status: List<ChargepointStatus>?,
    context: Context
): Int = if (status != null) {
    val unknown = status.any { it == ChargepointStatus.UNKNOWN }
    val available = status.count { it == ChargepointStatus.AVAILABLE }

    if (unknown) {
        ContextCompat.getColor(context, R.color.unknown)
    } else if (available > 0) {
        ContextCompat.getColor(context, R.color.available)
    } else {
        ContextCompat.getColor(context, R.color.unavailable)
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