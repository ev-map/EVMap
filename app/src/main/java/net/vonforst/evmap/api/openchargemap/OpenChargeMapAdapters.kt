package net.vonforst.evmap.api.openchargemap

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.ZonedDateTime

internal class ZonedDateTimeAdapter {
    @FromJson
    fun fromJson(value: String?): ZonedDateTime? = value?.let {
        ZonedDateTime.parse(value)
    }

    @ToJson
    fun toJson(value: ZonedDateTime?): String? = value?.toString()
}