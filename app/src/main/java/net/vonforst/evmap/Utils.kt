package net.vonforst.evmap

import android.graphics.Typeface
import android.os.Bundle
import android.text.*
import android.text.style.StyleSpan

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