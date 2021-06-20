package net.vonforst.evmap.api.openchargemap

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import net.vonforst.evmap.model.*
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
) {
    fun convert(refData: OCMReferenceData) = ChargeLocation(
        id,
        addressInfo.title,
        Coordinate(addressInfo.latitude, addressInfo.longitude),
        addressInfo.toAddress(refData),
        connections.map { it.convert(refData) },
        null,
        "https://openchargemap.org/site/poi/details/$id",
        null,
        recentlyVerified,
        null,
        null, //TODO: OperatorInfo
        generalComments,
        null,
        addressInfo.accessComments,
        null, // TODO: MediaItems,
        null,
        null,
        cost?.let { Cost(descriptionShort = it) }
    )
}

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
) {
    fun toAddress(refData: OCMReferenceData) = Address(
        town,
        refData.countries.find { it.id == countryId }!!.title,
        postcode,
        listOfNotNull(addressLine1, addressLine2).joinToString(", ")
    )
}

@JsonClass(generateAdapter = true)
data class OCMConnection(
    @Json(name = "ConnectionTypeID") val connectionTypeId: Long,
    @Json(name = "Amps") val amps: Int?,
    @Json(name = "Voltage") val voltage: Int?,
    @Json(name = "PowerKW") val power: Double?,
    @Json(name = "Quantity") val quantity: Int?,
    @Json(name = "Comments") val comments: String?
) {
    fun convert(refData: OCMReferenceData) = Chargepoint(
        convertConnectionType(connectionTypeId, refData),
        power ?: 0.0,
        quantity ?: 0
    )

    private fun convertConnectionType(id: Long, refData: OCMReferenceData): String {
        val title = refData.connectionTypes.find { it.id == id }!!.title
        return when (title) {
            "CCS (Type 2)" -> Chargepoint.CCS_TYPE_2
            "CHAdeMO" -> Chargepoint.CHADEMO
            "CEE 3 Pin" -> Chargepoint.CEE_BLAU
            "CEE 5 Pin" -> Chargepoint.CEE_ROT
            "CEE 7/4 - Schuko - Type F" -> Chargepoint.SCHUKO
            "Tesla (Roadster)" -> Chargepoint.TESLA_ROADSTER_HPC
            "Tesla Supercharger" -> Chargepoint.SUPERCHARGER
            "Type 2 (Socket Only)" -> Chargepoint.TYPE_2_SOCKET
            "Type 2 (Tethered Connector) " -> Chargepoint.TYPE_2_PLUG
            "Type 1 (J1772)" -> Chargepoint.TYPE_1
            "SCAME Type 3A (Low Power)" -> Chargepoint.TYPE_3
            "SCAME Type 3C (Schneider-Legrand)" -> Chargepoint.TYPE_3
            else -> title
        }
    }
}

@JsonClass(generateAdapter = true)
data class OCMReferenceData(
    @Json(name = "ConnectionTypes") val connectionTypes: List<OCMConnectionType>,
    @Json(name = "Countries") val countries: List<OCMCountry>
) : ReferenceData()

@JsonClass(generateAdapter = true)
data class OCMConnectionType(
    @Json(name = "ID") val id: Long,
    @Json(name = "Title") val title: String,
    @Json(name = "FormalName") val formalName: String?,
    @Json(name = "IsDiscontinued") val discontinued: Boolean?,
    @Json(name = "IsObsolete") val obsolete: Boolean?
)

@JsonClass(generateAdapter = true)
data class OCMCountry(
    @Json(name = "ID") val id: Long,
    @Json(name = "ISOCode") val isoCode: String,
    @Json(name = "ContinentCode") val continentCode: String?,
    @Json(name = "Title") val title: String
)