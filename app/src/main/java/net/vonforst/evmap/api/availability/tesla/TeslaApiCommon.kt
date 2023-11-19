package net.vonforst.evmap.api.availability.tesla

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.model.Coordinate
import java.time.LocalTime

sealed class GraphQlRequest {
    abstract val operationName: String
    abstract val query: String
    abstract val variables: Any?
}

fun Coordinate.asTeslaCoord() =
    TeslaChargingOwnershipGraphQlApi.Coordinate(this.lat, this.lng)

@JsonClass(generateAdapter = true)
data class Outage(val message: String /* TODO: */)

enum class ChargerAvailability {
    @Json(name = "CHARGER_AVAILABILITY_AVAILABLE")
    AVAILABLE,

    @Json(name = "CHARGER_AVAILABILITY_OCCUPIED")
    OCCUPIED,

    @Json(name = "CHARGER_AVAILABILITY_DOWN")
    DOWN,

    @Json(name = "CHARGER_AVAILABILITY_UNKNOWN")
    UNKNOWN;

    fun toStatus() = when (this) {
        AVAILABLE -> ChargepointStatus.AVAILABLE
        OCCUPIED -> ChargepointStatus.OCCUPIED
        DOWN -> ChargepointStatus.FAULTED
        UNKNOWN -> ChargepointStatus.UNKNOWN
    }
}

@JsonClass(generateAdapter = true)
data class Pricing(
    val canDisplayCombinedComparison: Boolean?,
    val hasMSPPricing: Boolean?,
    val hasMembershipPricing: Boolean?,
    val memberRates: Rates?, // rates for Tesla drivers & non-Tesla drivers with subscription
    val userRates: Rates?    // rates without subscription
)

@JsonClass(generateAdapter = true)
data class Rates(
    val activePricebook: Pricebook
)

@JsonClass(generateAdapter = true)
data class Pricebook(
    val charging: PricebookDetails,
    val parking: PricebookDetails,
    val priceBookID: Long?
)

@JsonClass(generateAdapter = true)
data class PricebookDetails(
    val bucketUom: String,  // unit of measurement for buckets (typically "kw")
    val buckets: List<Bucket>,  // buckets of charging power (used for minute-based pricing)
    val currencyCode: String,
    val programType: String,
    val rates: List<Double>,
    val touRates: TouRates,
    val uom: String,  // unit of measurement ("kwh" or "min")
    val vehicleMakeType: String
)

@JsonClass(generateAdapter = true)
data class Bucket(
    val start: Int,
    val end: Int
)

@JsonClass(generateAdapter = true)
data class TouRates(
    val activeRatesByTime: List<ActiveRatesByTime>,
    val enabled: Boolean
)

@JsonClass(generateAdapter = true)
data class ActiveRatesByTime(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val rates: List<Double>
)

internal class LocalTimeAdapter {
    @FromJson
    fun fromJson(value: String?): LocalTime? = value?.let {
        if (it == "24:00") LocalTime.MAX else LocalTime.parse(it)
    }

    @ToJson
    fun toJson(value: LocalTime?): String? = value?.toString()
}