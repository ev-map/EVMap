package net.vonforst.evmap.api.openchargemap

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

internal class ZonedDateTimeAdapter {
    @FromJson
    fun fromJson(value: String?): ZonedDateTime? = value?.let {
        try {
            ZonedDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            val dt: LocalDateTime = LocalDateTime.parse(value)
            dt.atZone(ZoneOffset.UTC)
        }
    }

    @ToJson
    fun toJson(value: ZonedDateTime?): String? = value?.toString()
}