package net.vonforst.evmap.api.goingelectric

import android.content.Context
import android.os.Parcelable
import androidx.core.text.HtmlCompat
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.Equatable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import kotlin.math.abs
import kotlin.math.floor

@JsonClass(generateAdapter = true)
data class ChargepointList(
    val status: String,
    val chargelocations: List<ChargepointListItem>
)

@JsonClass(generateAdapter = true)
data class StringList(
    val status: String,
    val result: List<String>
)

sealed class ChargepointListItem

@JsonClass(generateAdapter = true)
@Entity
data class ChargeLocation(
    @Json(name = "ge_id") @PrimaryKey val id: Long,
    val name: String,
    @Embedded val coordinates: Coordinate,
    @Embedded val address: Address,
    val chargepoints: List<Chargepoint>,
    @JsonObjectOrFalse val network: String?,
    val url: String,
    // @Json(name = "fault_report") val faultReport: Boolean, <- Object or false in detail, true or false in overview
    val verified: Boolean,
    // only shown in details:
    @JsonObjectOrFalse val operator: String?,
    @JsonObjectOrFalse @Json(name = "general_information") val generalInformation: String?,
    @JsonObjectOrFalse @Json(name = "ladeweile") val amenities: String?,
    @JsonObjectOrFalse @Json(name = "location_description") val locationDescription: String?,
    val photos: List<ChargerPhoto>?,
    //val chargecards: Boolean?
    @Embedded val openinghours: OpeningHours?,
    @Embedded val cost: Cost?
) : ChargepointListItem(), Equatable {
    val maxPower: Double
        get() {
            return chargepoints.map { it.power }.max() ?: 0.0
        }

    /**
     * Merges chargepoints if they have the same plug and power
     *
     * This occurs e.g. for Type2 sockets and plugs, which are distinct on the GE website, but not
     * separable in the API
     */
    val chargepointsMerged: List<Chargepoint>
        get() {
            val variants = chargepoints.distinctBy { it.power to it.type }
            return variants.map { variant ->
                val count = chargepoints
                    .filter { it.type == variant.type && it.power == variant.power }
                    .sumBy { it.count }
                Chargepoint(variant.type, variant.power, count)
            }
        }

    fun formatChargepoints(): String {
        return chargepointsMerged.map {
            "${it.count} × ${it.type} ${it.formatPower()}"
        }.joinToString(" · ")
    }
}

@JsonClass(generateAdapter = true)
data class Cost(
    val freecharging: Boolean,
    val freeparking: Boolean,
    @JsonObjectOrFalse @Json(name = "description_short") val descriptionShort: String?,
    @JsonObjectOrFalse @Json(name = "description_long") val descriptionLong: String?
) {
    fun getStatusText(ctx: Context): CharSequence {
        return HtmlCompat.fromHtml(
            ctx.getString(
                R.string.cost_detail,
                if (freecharging) ctx.getString(R.string.free) else ctx.getString(R.string.paid),
                if (freeparking) ctx.getString(R.string.free) else ctx.getString(R.string.paid)
            ), 0
        )
    }
}

@JsonClass(generateAdapter = true)
data class OpeningHours(
    @Json(name = "24/7") val twentyfourSeven: Boolean,
    @JsonObjectOrFalse val description: String?,
    @Embedded val days: OpeningHoursDays?
) {
    val isEmpty: Boolean
        get() = description == "Leider noch keine Informationen zu Öffnungszeiten vorhanden."
                && days == null && !twentyfourSeven

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
        } else {
            return ""
        }
    }
}

@JsonClass(generateAdapter = true)
data class OpeningHoursDays(
    @Embedded(prefix = "mo") val monday: Hours,
    @Embedded(prefix = "tu") val tuesday: Hours,
    @Embedded(prefix = "we") val wednesday: Hours,
    @Embedded(prefix = "th") val thursday: Hours,
    @Embedded(prefix = "fr") val friday: Hours,
    @Embedded(prefix = "sa") val saturday: Hours,
    @Embedded(prefix = "su") val sunday: Hours,
    @Embedded(prefix = "ho") val holiday: Hours
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
@Parcelize
data class ChargerPhoto(val id: String) : Parcelable

@JsonClass(generateAdapter = true)
data class ChargeLocationCluster(
    val clusterCount: Int,
    val coordinates: Coordinate
) : ChargepointListItem()

@JsonClass(generateAdapter = true)
data class Coordinate(val lat: Double, val lng: Double) {
    fun formatDMS(): String {
        return "${dms(lat, false)}, ${dms(lng, true)}"
    }

    private fun dms(value: Double, lon: Boolean): String {
        val hemisphere = if (lon) {
            if (value >= 0) "E" else "W"
        } else {
            if (value >= 0) "N" else "S"
        }
        val d = abs(value)
        val degrees = floor(d).toInt()
        val minutes = floor((d - degrees) * 60).toInt()
        val seconds = ((d - degrees) * 60 - minutes) * 60
        return "%d°%02d'%02.1f\"%s".format(Locale.ENGLISH, degrees, minutes, seconds, hemisphere)
    }

    fun formatDecimal(): String {
        return "%.6f, %.6f".format(Locale.ENGLISH, lat, lng)
    }
}

@JsonClass(generateAdapter = true)
data class Address(
    @JsonObjectOrFalse val city: String?,
    @JsonObjectOrFalse val country: String?,
    @JsonObjectOrFalse val postcode: String?,
    @JsonObjectOrFalse val street: String?
) {
    override fun toString(): String {
        return "${street ?: ""}, ${postcode ?: ""} ${city ?: ""}"
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

    companion object {
        const val TYPE_1 = "Typ1"
        const val TYPE_2 = "Typ2"
        const val TYPE_3 = "Typ3"
        const val CCS = "CCS"
        const val SCHUKO = "Schuko"
        const val CHADEMO = "CHAdeMO"
        const val SUPERCHARGER = "Tesla Supercharger"
        const val CEE_BLAU = "CEE Blau"
        const val CEE_ROT = "CEE Rot"
    }
}