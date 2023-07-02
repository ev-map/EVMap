package net.vonforst.evmap.api.openstreetmap

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant
import kotlin.math.floor

internal class InstantAdapter {
    @FromJson
    fun fromJson(value: Double?): Instant? = value?.let {
        val seconds = floor(it).toLong()
        val nanos = ((value - seconds) * 1e9).toLong()
        Instant.ofEpochSecond(seconds, nanos)
    }

    @ToJson
    fun toJson(value: Instant?): Double? = value?.let {
        it.epochSecond.toDouble() + it.nano / 1e9
    }
}