package net.vonforst.evmap.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import android.view.View
import androidx.core.text.getSpans
import net.vonforst.evmap.MapsActivity

class CustomUrlSpan(url: String): URLSpan(url) {
    override fun onClick(widget: View) {
        (widget.context as? MapsActivity)?.let {
            it.openUrl(url, widget.rootView)
        } ?: {
            super.onClick(widget)
        }
    }
}

fun Spanned.replaceUrlSpansWithCustom(): Spanned {
    val builder = SpannableStringBuilder(this)
    builder.getSpans<URLSpan>().forEach {
        builder.setSpan(CustomUrlSpan(it.url), builder.getSpanStart(it), builder.getSpanEnd(it), builder.getSpanFlags(it))
        builder.removeSpan(it)
    }
    return builder
}