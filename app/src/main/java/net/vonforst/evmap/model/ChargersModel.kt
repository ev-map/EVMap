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

sealed class ChargepointListItem


/**
 * A whole charging site (potentially with multiple chargepoints).
 *
 * @param id A unique number per charging site
 * @param dataSource The name of the data source
 * @param coordinates The latitude / longitude of this charge location
 * @param address The charge location address
 * @param chargepoints List of chargepoints at this location
 * @param network The charging network (Mobility Service Provider, MSP)
 * @param url A link to this charging site
 * @param editUrl A link to a website where this charging site can be edited
 * @param faultReport Set this if the charging site is reported to be out of service
 * @param verified For crowdsourced data sources, this means that the data has been verified
 *   by an independent person
 * @param barrierFree Whether this charge location can be used without prior registration
 * @param operator The operator of this charge location (Charge Point Operator, CPO)
 * @param generalInformation General information about this charging site that does not fit anywhere else
 * @param amenities Description of amenities available at or near the charging site (toilets, food, accommodation, landmarks, etc.)
 * @param locationDescription Directions on how to find the charger (e.g. "In the parking garage on level 5")
 * @param photos List of photos of this charging site
 * @param chargecards List of charge cards accepted here
 * @param openinghours List of times when this charging site can be accessed / used
 * @param cost The cost for charging and/or parking
 * @param license How the data about this chargepoint is licensed
 * @param chargepriceData Additional data needed for the Chargeprice implementation
 * @param timeRetrieved Time when this information was retrieved from the data source
 * @param isDetailed Whether this data includes all available details (for many data sources,
 *                   API calls that return a list may only give a compact representation)
 */
@Entity(primaryKeys = ["id", "dataSource"])
@Parcelize
data class ChargeLocation(
    val id: Long,
    val dataSource: String,
    val name: String,
    val coordinates: Coordinate,
    @Embedded val address: Address?,
    val chargepoints: List<Chargepoint>,
    val network: String?,
    val url: String,  // URL of this charger at the data source
    val editUrl: String?,  // URL to edit this charger at the data source
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
    @Embedded(prefix = "chargeprice") val chargepriceData: ChargepriceData?,
    val networkUrl: String?,  // Website of the network
    val chargerUrl: String?,  // Website for this specific charging site. Might be an ad-hoc payment page.
    val timeRetrieved: Instant,
    val isDetailed: Boolean
) : ChargepointListItem(), Equatable, Parcelable {
    /**
     * maximum power available from this charger.
     */
    val maxPower: Double?
        get() {
            return maxPower()
        }

    /**
     * Gets the maximum power available from certain connectors of this charger.
     */
    fun maxPower(connectors: Set<String>? = null): Double? {
        return chargepoints
            .filter { connectors?.contains(it.type) ?: true }
            .mapNotNull { it.power }
            .maxOrNull()
    }

    fun isMulti(filteredConnectors: Set<String>? = null): Boolean {
        var chargepoints = chargepointsMerged
            .filter { filteredConnectors?.contains(it.type) ?: true }
        val chargepointMaxPower = maxPower(filteredConnectors)
        if (chargepointMaxPower != null && chargepointMaxPower >= 43) {
            // fast charger -> only count fast chargers
            chargepoints = chargepoints.filter {it.power != null && it.power >= 43 }
        }
        val connectors = chargepoints.map { it.type }.distinct().toSet()

        // check if there is more than one plug for any connector type
        val chargepointsPerConnector =
            connectors.map { conn -> chargepoints.filter { it.type == conn }.sumOf { it.count } }
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
                    .sumOf { it.count }
                Chargepoint(variant.type, variant.power, count)
            }
        }

    val totalChargepoints: Int
        get() = chargepoints.sumOf { it.count }

    fun formatChargepoints(sp: StringProvider): String {
        return chargepointsMerged.joinToString(" · ") {
            "${it.count} × ${nameForPlugType(sp, it.type)}${
                it.formatPower()?.let { power -> " $power" } ?: ""
            }"
        }
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
                if (freecharging) ctx.getString(R.string.charging_free) else ctx.getString(R.string.charging_paid)
            val parking =
                if (freeparking) ctx.getString(R.string.parking_free) else ctx.getString(R.string.parking_paid)
            return if (emoji) {
                "⚡ $charging · \uD83C\uDD7F️ $parking"
            } else {
                HtmlCompat.fromHtml(ctx.getString(R.string.cost_detail, charging, parking), 0)
            }
        } else if (freecharging != null) {
            val charging =
                if (freecharging) ctx.getString(R.string.charging_free) else ctx.getString(R.string.charging_paid)
            return if (emoji) {
                "⚡ $charging"
            } else {
                HtmlCompat.fromHtml(ctx.getString(R.string.cost_detail_charging, charging), 0)
            }
        } else if (freeparking != null) {
            val parking =
                if (freeparking) ctx.getString(R.string.parking_free) else ctx.getString(R.string.parking_paid)
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

    fun getDetailText(): CharSequence? {
        return if (freecharging == null && freeparking == null) {
            if (descriptionShort != null && descriptionLong != descriptionShort) {
                descriptionLong
            } else {
                null
            }
        } else {
            descriptionLong ?: descriptionShort
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
            val today = LocalDate.now()
            val hours = days.getHoursForDate(today)
            val nextDayHours = days.getHoursForDate(today.plusDays(1))
            val previousDayHours = days.getHoursForDate(today.minusDays(1))

            val now = LocalTime.now()
            val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

            if (previousDayHours != null && previousDayHours.end.isBefore(previousDayHours.start) && previousDayHours.end.isAfter(
                    now
                )
            ) {
                // previous day has opening hours that go past midnight
                return HtmlCompat.fromHtml(
                    ctx.getString(
                        R.string.open_closesat,
                        previousDayHours.end.format(fmt)
                    ), 0
                )
            } else if (hours != null && hours.start.isBefore(hours.end)
                && hours.start.isBefore(now) && hours.end.isAfter(now)
            ) {
                // current day has opening hours that do not go past midnight
                return HtmlCompat.fromHtml(
                    ctx.getString(
                        R.string.open_closesat,
                        hours.end.format(fmt)
                    ), 0
                )
            } else if (hours != null && hours.end.isBefore(hours.start)
                && hours.start.isBefore(now)
            ) {
                // current day has opening hours that go past midnight
                return HtmlCompat.fromHtml(
                    ctx.getString(
                        R.string.open_closesat,
                        hours.end.format(fmt)
                    ), 0
                )
            } else if (hours != null && !hours.start.isBefore(now)) {
                // currently closed, will still open on this day
                return HtmlCompat.fromHtml(
                    ctx.getString(
                        R.string.closed_opensat,
                        hours.start.format(fmt)
                    ), 0
                )
            } else if (nextDayHours != null) {
                // currently closed, will open next day
                return HtmlCompat.fromHtml(
                    ctx.getString(
                        R.string.closed_opensat,
                        nextDayHours.start.format(fmt)
                    ), 0
                )
            } else {
                return HtmlCompat.fromHtml(ctx.getString(R.string.closed), 0)
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
    /**
     * Gets a URL of the image corresponding to a given size.
     *
     * If the data source supports accessing the image in its original (potentially unlimited) size,
     * this size will only be returned if allowOriginal is set to true. Otherwise, only scaled
     * versions of the images will be returned.
     */
    abstract fun getUrl(
        height: Int? = null,
        width: Int? = null,
        size: Int? = null,
        allowOriginal: Boolean = false
    ): String
}

data class ChargeLocationCluster(
    val clusterCount: Int,
    val coordinates: Coordinate,
    val items: List<ChargeLocation>? = null
) : ChargepointListItem()

@Parcelize
data class Coordinate(val lat: Double, val lng: Double) : Parcelable

@Parcelize
data class Address(
    val city: String?,
    val country: String?,
    val postcode: String?,
    val street: String?
) : Parcelable {
    override fun toString(): String {
        // TODO: the order here follows a German-style format (i.e. street, postcode city).
        // in principle this should be country-dependent (e.g. UK has postcode after city)
        return buildString {
            street?.let {
                append(it)
                append(", ")
            }
            postcode?.let {
                append(it)
                append(" ")
            }
            city?.let {
                append(it)
            }
        }
    }
}

/**
 * One socket with a certain power, which may be available multiple times at a ChargeLocation.
 */
@Parcelize
@JsonClass(generateAdapter = true)
data class Chargepoint(
    // The chargepoint type (use one of the constants in the companion object)
    val type: String,
    // Power in kW (or null if unknown)
    val power: Double?,
    // How many instances of this plug/socket are available?
    val count: Int,
) : Equatable, Parcelable {
    fun hasKnownPower(): Boolean = power != null

    /**
     * If chargepoint power is defined, format it into a string.
     * Otherwise, return null.
     */
    fun formatPower(): String? {
        if (power == null) {
            return null
        }
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