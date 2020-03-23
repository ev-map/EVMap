package com.johan.evmap.api

import android.content.Context
import androidx.core.text.HtmlCompat
import com.johan.evmap.R
import com.johan.evmap.adapter.Equatable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalTime

@JsonClass(generateAdapter = true)
data class ChargepointList(
    val status: String,
    val chargelocations: List<ChargepointListItem>
)

sealed class ChargepointListItem

@JsonClass(generateAdapter = true)
data class ChargeLocation(
    @Json(name = "ge_id") val id: Long,
    val name: String,
    val coordinates: Coordinate,
    val address: Address,
    val chargepoints: List<Chargepoint>,
    @JsonObjectOrFalse val network: String?,
    val url: String,
    // @Json(name = "fault_report") val faultReport: Boolean, <- Object or false in detail, true or false in overview
    val verified: Boolean,
    // only shown in details:
    @JsonObjectOrFalse val operator: String?,
    @Json(name = "general_information") @JsonObjectOrFalse val generalInformation: String?,
    val photos: List<ChargerPhoto>?,
    //val chargecards: Boolean?
    val openinghours: OpeningHours?
) : ChargepointListItem() {
    val maxPower: Double
        get() {
            return chargepoints.map { it.power }.max() ?: 0.0
        }

    fun formatChargepoints(): String {
        return chargepoints.map {
            "${it.count} × ${it.type} ${it.formatPower()}"
        }.joinToString(" · ")
    }
}

@JsonClass(generateAdapter = true)
data class OpeningHours(
    @Json(name = "24/7") val twentyfourSeven: Boolean,
    @JsonObjectOrFalse val description: String?,
    val days: OpeningHoursDays?
) {
    fun getStatusText(ctx: Context): CharSequence {
        if (twentyfourSeven) {
            return HtmlCompat.fromHtml(ctx.getString(R.string.open_247), 0)
        } else if (days != null) {
            val hours = days.getHoursForDate(LocalDate.now())
            if (hours.start == null || hours.end == null) {
                return HtmlCompat.fromHtml(ctx.getString(R.string.closed), 0)
            }

            val now = LocalTime.now()
            if (hours.start.isBefore(now) && hours.end.isAfter(now)) {
                return HtmlCompat.fromHtml(
                    ctx.getString(
                        R.string.open_closesat,
                        hours.end.toString()
                    ), 0
                )
            } else if (hours.end.isBefore(now)) {
                return HtmlCompat.fromHtml(ctx.getString(R.string.closed), 0)
            } else {
                return HtmlCompat.fromHtml(
                    ctx.getString(
                        R.string.closed_opensat,
                        hours.start.toString()
                    ), 0
                )
            }
        } else if (description != null) {
            return description
        } else {
            return ""
        }
    }
}

@JsonClass(generateAdapter = true)
data class OpeningHoursDays(
    val monday: Hours,
    val tuesday: Hours,
    val wednesday: Hours,
    val thursday: Hours,
    val friday: Hours,
    val saturday: Hours,
    val sunday: Hours,
    val holiday: Hours
) {
    fun getHoursForDate(date: LocalDate): Hours {
        // TODO: check for holidays

        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> monday
            DayOfWeek.TUESDAY -> tuesday
            DayOfWeek.WEDNESDAY -> wednesday
            DayOfWeek.THURSDAY -> thursday
            DayOfWeek.FRIDAY -> friday
            DayOfWeek.SATURDAY -> saturday
            DayOfWeek.SUNDAY -> sunday
        }
    }
}

data class Hours(
    val start: LocalTime?,
    val end: LocalTime?
)

@JsonClass(generateAdapter = true)
data class ChargerPhoto(val id: String)

@JsonClass(generateAdapter = true)
data class ChargeLocationCluster(
    val clusterCount: Int,
    val coordinates: Coordinate
) : ChargepointListItem()

@JsonClass(generateAdapter = true)
data class Coordinate(val lat: Double, val lng: Double)

@JsonClass(generateAdapter = true)
data class Address(
    val city: String,
    val country: String,
    val postcode: String,
    val street: String
) {
    override fun toString(): String {
        return "$street, $postcode $city"
    }
}

@JsonClass(generateAdapter = true)
data class Chargepoint(val type: String, val power: Double, val count: Int) : Equatable {
    fun formatPower(): String {
        val powerFmt = if (power - power.toInt() == 0.0) {
            "%.0f".format(power)
        } else {
            "%.1f".format(power)
        }
        return "$powerFmt kW"
    }
}