package net.vonforst.evmap.model

import android.content.Context
import android.os.Parcelable
import androidx.core.text.HtmlCompat
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.nameForPlugType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.math.abs
import kotlin.math.floor

sealed class ChargepointListItem

@Entity(primaryKeys = ["id", "dataSource"])
@Parcelize
data class ChargeLocation(
    val id: Long,
    val dataSource: String,
    val name: String,
    @Embedded val coordinates: Coordinate,
    @Embedded val address: Address,
    val chargepoints: List<Chargepoint>,
    val network: String?,
    val url: String,
    val editUrl: String?,
    @Embedded(prefix = "fault_report_") val faultReport: FaultReport?,
    val verified: Boolean,
    val barrierFree: Boolean?,
    // only shown in details:
    val operator: String?,
    val generalInformation: String?,
    val amenities: String?,
    val locationDescription: String?,
    val photos: List<ChargerPhoto>?,
    val chargecards: List<ChargeCardId>?,
    @Embedded val openinghours: OpeningHours?,
    @Embedded val cost: Cost?,
    val license: String?,
    @Embedded(prefix = "chargeprice") val chargepriceData: ChargepriceData?
) : ChargepointListItem(), Equatable, Parcelable {
    /**
     * maximum power available from this charger.
     */
    val maxPower: Double
        get() {
            return maxPower()
        }

    /**
     * Gets the maximum power available from certain connectors of this charger.
     */
    fun maxPower(connectors: Set<String>? = null): Double {
        return chargepoints.filter { connectors?.contains(it.type) ?: true }
            .map { it.power }.maxOrNull() ?: 0.0
    }

    fun isMulti(filteredConnectors: Set<String>? = null): Boolean {
        var chargepoints = chargepointsMerged
            .filter { filteredConnectors?.contains(it.type) ?: true }
        if (maxPower(filteredConnectors) >= 43) {
            // fast charger -> only count fast chargers
            chargepoints = chargepoints.filter { it.power >= 43 }
        }
        val connectors = chargepoints.map { it.type }.distinct().toSet()

        // check if there is more than one plug for any connector type
        val chargepointsPerConnector =
            connectors.map { conn -> chargepoints.filter { it.type == conn }.sumBy { it.count } }
        return chargepointsPerConnector.any { it > 1 }
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

    val totalChargepoints: Int
        get() = chargepoints.sumBy { it.count }

    fun formatChargepoints(sp: StringProvider): String {
        return chargepointsMerged.map {
            "${it.count} × ${nameForPlugType(sp, it.type)} ${it.formatPower()}"
        }.joinToString(" · ")
    }
}

/**
 * Additional data needed for the Chargeprice implementation
 */
@Parcelize
data class ChargepriceData(
    val country: String?,
    val network: String?,
    val plugTypes: List<String>?
) : Parcelable

@Parcelize
data class Cost(
    val freecharging: Boolean? = null,
    val freeparking: Boolean? = null,
    val descriptionShort: String? = null,
    val descriptionLong: String? = null
) : Parcelable {
    val isEmpty: Boolean
        get() = descriptionLong == null && descriptionShort == null && freecharging == null && freeparking == null

    fun getStatusText(ctx: Context, emoji: Boolean = false): CharSequence {
        if (freecharging != null && freeparking != null) {
            val charging =
                if (freecharging) ctx.getString(R.string.free) else ctx.getString(R.string.paid)
            val parking =
                if (freeparking) ctx.getString(R.string.free) else ctx.getString(R.string.paid)
            return if (emoji) {
                "⚡ $charging · \uD83C\uDD7F️ $parking"
            } else {
                HtmlCompat.fromHtml(ctx.getString(R.string.cost_detail, charging, parking), 0)
            }
        } else if (freecharging != null) {
            val charging =
                if (freecharging) ctx.getString(R.string.free) else ctx.getString(R.string.paid)
            return if (emoji) {
                "⚡ $charging"
            } else {
                HtmlCompat.fromHtml(ctx.getString(R.string.cost_detail_charging, charging), 0)
            }
        } else if (freeparking != null) {
            val parking =
                if (freeparking) ctx.getString(R.string.free) else ctx.getString(R.string.paid)
            return if (emoji) {
                "\uD83C\uDD7F $parking"
            } else {
                HtmlCompat.fromHtml(ctx.getString(R.string.cost_detail_parking, parking), 0)
            }
        } else if (descriptionShort != null) {
            return descriptionShort
        } else if (descriptionLong != null) {
            return descriptionLong
        } else {
            return ""
        }
    }
}

@Parcelize
data class OpeningHours(
    val twentyfourSeven: Boolean,
    val description: String?,
    @Embedded val days: OpeningHoursDays?
) : Parcelable {
    val isEmpty: Boolean
        get() = description == "Leider noch keine Informationen zu Öffnungszeiten vorhanden."
                && days == null && !twentyfourSeven

    fun getStatusText(ctx: Context): CharSequence {
        if (twentyfourSeven) {
            return HtmlCompat.fromHtml(ctx.getString(R.string.open_247), 0)
        } else if (days != null) {
            val hours = days.getHoursForDate(LocalDate.now())
                ?: return HtmlCompat.fromHtml(ctx.getString(R.string.closed), 0)

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

@Parcelize
data class OpeningHoursDays(
    @Embedded(prefix = "mo") val monday: Hours?,
    @Embedded(prefix = "tu") val tuesday: Hours?,
    @Embedded(prefix = "we") val wednesday: Hours?,
    @Embedded(prefix = "th") val thursday: Hours?,
    @Embedded(prefix = "fr") val friday: Hours?,
    @Embedded(prefix = "sa") val saturday: Hours?,
    @Embedded(prefix = "su") val sunday: Hours?,
    @Embedded(prefix = "ho") val holiday: Hours?
) : Parcelable {
    fun getHoursForDate(date: LocalDate): Hours? {
        // TODO: check for holidays
        return getHoursForDayOfWeek(date.dayOfWeek)
    }

    fun getHoursForDayOfWeek(dayOfWeek: DayOfWeek?): Hours? {
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> monday
            DayOfWeek.TUESDAY -> tuesday
            DayOfWeek.WEDNESDAY -> wednesday
            DayOfWeek.THURSDAY -> thursday
            DayOfWeek.FRIDAY -> friday
            DayOfWeek.SATURDAY -> saturday
            DayOfWeek.SUNDAY -> sunday
            null -> holiday
        }
    }
}

@Parcelize
data class Hours(
    val start: LocalTime,
    val end: LocalTime
) : Parcelable {
    override fun toString(): String {
        val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        return "${start.format(fmt)} - ${end.format(fmt)}"
    }
}

abstract class ChargerPhoto(open val id: String) : Parcelable {
    abstract fun getUrl(height: Int? = null, width: Int? = null, size: Int? = null): String
}

data class ChargeLocationCluster(
    val clusterCount: Int,
    val coordinates: Coordinate
) : ChargepointListItem()

@Parcelize
data class Coordinate(val lat: Double, val lng: Double) : Parcelable {
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

@Parcelize
data class Address(
    val city: String?,
    val country: String?,
    val postcode: String?,
    val street: String?
) : Parcelable {
    override fun toString(): String {
        return "${street ?: ""}, ${postcode ?: ""} ${city ?: ""}"
    }
}

@Parcelize
@JsonClass(generateAdapter = true)
data class Chargepoint(val type: String, val power: Double, val count: Int) : Equatable,
    Parcelable {
    fun formatPower(): String {
        val powerFmt = if (power - power.toInt() == 0.0) {
            "%.0f".format(power)
        } else {
            "%.1f".format(power)
        }
        return "$powerFmt kW"
    }

    companion object {
        const val TYPE_1 = "Type 1"
        const val TYPE_2_UNKNOWN = "Type 2 (either plug or socket)"
        const val TYPE_2_SOCKET = "Type 2 socket"
        const val TYPE_2_PLUG = "Type 2 plug"
        const val TYPE_3 = "Type 3"
        const val CCS_TYPE_2 = "CCS Type 2"
        const val CCS_TYPE_1 = "CCS Type 1"
        const val CCS_UNKNOWN = "CCS (either Type 1 or Type 2)"
        const val SCHUKO = "Schuko"
        const val CHADEMO = "CHAdeMO"
        const val SUPERCHARGER = "Tesla Supercharger"
        const val CEE_BLAU = "CEE Blau"
        const val CEE_ROT = "CEE Rot"
        const val TESLA_ROADSTER_HPC = "Tesla HPC"
    }
}

@Parcelize
data class FaultReport(val created: Instant?, val description: String?) : Parcelable

@Entity
data class ChargeCard(
    @PrimaryKey val id: Long,
    val name: String,
    val url: String
)

@Parcelize
@JsonClass(generateAdapter = true)
data class ChargeCardId(
    val id: Long
) : Parcelable