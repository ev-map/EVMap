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
    //val network: String,
    val url: String,
    @Json(name = "fault_report") val faultReport: Boolean,
    val verified: Boolean
) : ChargepointListItem() {
    val maxPower: Double
        get() {
            return chargepoints.map { it.power }.max() ?: 0.0
        }
}

@JsonClass(generateAdapter = true)
data class ChargeLocationCluster(
    val clusterCount: Int,
    val coordinates: Coordinate
) : ChargepointListItem()

@JsonClass(generateAdapter = true)
data class Coordinate(val lat: Double, val lng: Double)

@JsonClass(generateAdapter = true)
data class Address(val city: String, val country: String, val postcode: String, val street: String)

@JsonClass(generateAdapter = true)
data class Chargepoint(val type: String, val power: Double, val count: Int)