package net.vonforst.evmap.api.nobil

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import net.vonforst.evmap.model.Coordinate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class CoordinateAdapter {
    @FromJson
    fun fromJson(position: String): Coordinate {
        val pattern = """\((?<lat>\d+(\.\d+)?), *(?<long>-?\d+(\.\d+)?)\)"""
        val match = Regex(pattern).matchEntire(position)
        if (match == null) throw JsonDataException("Unexpected coordinate format: '$position'")

        val groups = match.groups
        val latitude : String = groups["lat"]?.value?: "0.0"
        val longitude : String = groups["long"]?.value?: "0.0"
        return Coordinate(latitude.toDouble(), longitude.toDouble())
    }
    @ToJson
    fun toJson(value: Coordinate): String = "(" + value.lat + ", " + value.lng + ")"
}

internal class LocalDateTimeAdapter {
    @FromJson
    fun fromJson(value: String?): LocalDateTime? = value?.let {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    @ToJson
    fun toJson(value: LocalDateTime?): String? = value?.toString()
}
