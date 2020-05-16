package net.vonforst.evmap

import android.os.Bundle

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