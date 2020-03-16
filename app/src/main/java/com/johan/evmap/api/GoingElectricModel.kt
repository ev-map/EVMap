package com.johan.evmap.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

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
    val photos: List<ChargerPhoto>?
    //val chargecards: Boolean?
) : ChargepointListItem() {
    val maxPower: Double
        get() {
            return chargepoints.map { it.power }.max() ?: 0.0
        }

    fun formatChargepoints(): String {
        return chargepoints.map {
            val powerFmt = if (it.power - it.power.toInt() == 0.0) {
                "%.0f".format(it.power)
            } else {
                "%.1f".format(it.power)
            }
            "${it.count}x ${it.type} $powerFmt kW"
        }.joinToString(" · ")
    }
}

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
data class Chargepoint(val type: String, val power: Double, val count: Int)