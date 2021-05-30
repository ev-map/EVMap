package net.vonforst.evmap.api.openchargemap

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.ZonedDateTime

data class OCMBoundingBox(
    val sw_lat: Double, val sw_lng: Double,
    val ne_lat: Double, val ne_lng: Double
) {
    override fun toString(): String {
        return "($sw_lat,$sw_lng),($ne_lat,$ne_lng)"
    }
}

@JsonClass(generateAdapter = true)
data class OCMChargepoint(
    @Json(name = "ID") val id: Long,
    @Json(name = "IsRecentlyVerified") val recentlyVerified: Boolean,
    @Json(name = "DateLastVerified") val dateLastVerified: ZonedDateTime?,
    @Json(name = "UsageCost") val cost: String?,
    @Json(name = "AddressInfo") val addressInfo: OCMAddressInfo,
    @Json(name = "Connections") val connections: List<OCMConnection>,
    @Json(name = "NumberOfPoints") val numPoints: Int,
    @Json(name = "GeneralComments") val generalComments: String?
)

@JsonClass(generateAdapter = true)
data class OCMAddressInfo(
    @Json(name = "Title") val title: String,
    @Json(name = "AddressLine1") val addressLine1: String?,
    @Json(name = "AddressLine2") val addressLine2: String?,
    @Json(name = "Town") val town: String?,
    @Json(name = "StateOrProvince") val stateOrProvince: String?,
    @Json(name = "Postcode") val postcode: String?,
    @Json(name = "CountryID") val countryId: Long,
    @Json(name = "Latitude") val latitude: Double,
    @Json(name = "Longitude") val longitude: Double,
    @Json(name = "ContactTelephone1") val contactTelephone1: String?,
    @Json(name = "ContactTelephone2") val contactTelephone2: String?,
    @Json(name = "ContactEmail") val contactEmail: String?,
    @Json(name = "AccessComments") val accessComments: String?,
    @Json(name = "RelatedURL") val relatedUrl: String?
)

@JsonClass(generateAdapter = true)
data class OCMConnection(
    @Json(name = "ConnectionTypeID") val connectionTypeId: Long,
    @Json(name = "Amps") val amps: Int,
    @Json(name = "Voltage") val voltage: Int,
    @Json(name = "PowerKW") val power: Double,
    @Json(name = "Quantity") val quantity: Int,
    @Json(name = "Comments") val comments: String?
)