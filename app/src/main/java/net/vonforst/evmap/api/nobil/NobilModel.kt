package net.vonforst.evmap.api.nobil

import android.net.Uri
import androidx.core.text.HtmlCompat
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import net.vonforst.evmap.max
import net.vonforst.evmap.model.Address
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.model.ChargerPhoto
import net.vonforst.evmap.model.Coordinate
import net.vonforst.evmap.model.Cost
import net.vonforst.evmap.model.FilterValues
import net.vonforst.evmap.model.OpeningHours
import net.vonforst.evmap.model.ReferenceData
import net.vonforst.evmap.model.getBooleanValue
import net.vonforst.evmap.model.getMultipleChoiceValue
import net.vonforst.evmap.model.getSliderValue
import java.time.Instant
import java.time.LocalDateTime

data class NobilReferenceData(
    val dummy: Int
) : ReferenceData()

@JsonClass(generateAdapter = true)
data class NobilNumChargepointsRequest(
    val apikey: String,
    val countrycode: String,
    val action: String = "search",
    val type: String = "stats_GetSumChargerstations",
    val format: String = "json",
    val apiversion: String = "3"
)

@JsonClass(generateAdapter = true)
data class NobilRectangleSearchRequest(
    val apikey: String,
    val northeast: String,
    val southwest: String,
    val limit: Int,
    val action: String = "search",
    val type: String = "rectangle",
    val format: String = "json",
    val apiversion: String = "3",
    // val existingids: String
)

@JsonClass(generateAdapter = true)
data class NobilRadiusSearchRequest(
    val apikey: String,
    val lat: Double,
    val long: Double,
    val distance: Double, // meters
    val limit: Int,
    val action: String = "search",
    val type: String = "near",
    val format: String = "json",
    val apiversion: String = "3",
    // val existingids: String,
)

@JsonClass(generateAdapter = true)
data class NobilDetailSearchRequest(
    val apikey: String,
    val id: String,
    val action: String = "search",
    val type: String = "id",
    val format: String = "json",
    val apiversion: String = "3",
)

@JsonClass(generateAdapter = true)
data class NobilResponseData(
    @Json(name = "error") val error: String?,
    @Json(name = "Provider") val provider: String?,
    @Json(name = "Rights") val rights: String?,
    @Json(name = "apiver") val apiver: String?,
    @Json(name = "chargerstations") val chargerStations: List<NobilChargerStation>?
)

data class NobilNumChargepointsResponseData(
    val error: String?,
    val provider: String?,
    val rights: String?,
    val apiver: String?,
    val count: Int?
)

data class NobilDynamicResponseData(
    val error: String?,
    val provider: String?,
    val rights: String?,
    val apiver: String?,
    val chargerStations: Sequence<NobilChargerStation>?
)

@JsonClass(generateAdapter = true)
data class NobilChargerStation(
    @Json(name = "csmd") val chargerStationData: NobilChargerStationData,
    @Json(name = "attr") val chargerStationAttributes: NobilChargerStationAttributes
) {
    fun convert(dataLicense: String,
                filters: FilterValues?) : ChargeLocation? {
        val chargepoints = chargerStationAttributes.conn
            .mapNotNull { createChargepointFromNobilConnection(it.value) }
        if (chargepoints.isEmpty()) return null

        val minPower = filters?.getSliderValue("min_power")
        val connectors = filters?.getMultipleChoiceValue("connectors")
        val minConnectors = filters?.getSliderValue("min_connectors")
        if (chargepoints
            .filter { it.power != null && it.power >= (minPower ?: 0) }
            .filter { if (connectors != null && !connectors.all) it.type in connectors.values else true }
            .size < (minConnectors ?: 0)) return null

        val chargeLocation = ChargeLocation(
            chargerStationData.id,
            "nobil",
            HtmlCompat.fromHtml(chargerStationData.name, HtmlCompat.FROM_HTML_MODE_COMPACT)
                .toString(),
            chargerStationData.position,
            Address(
                chargerStationData.city,
                when (chargerStationData.landCode) {
                    "DAN" -> "Denmark"
                    "FIN" -> "Finland"
                    "ISL" -> "Iceland"
                    "NOR" -> "Norway"
                    "SWE" -> "Sweden"
                    else -> ""
                },
                chargerStationData.zipCode,
                listOfNotNull(
                    chargerStationData.street,
                    chargerStationData.houseNumber
                ).joinToString(" ")
            ),
            chargepoints,
            if (chargerStationData.operator != null) HtmlCompat.fromHtml(
                chargerStationData.operator,
                HtmlCompat.FROM_HTML_MODE_COMPACT
            ).toString() else null,
            "https://nobil.no/",
            null,
            when (chargerStationData.landCode) {
                "SWE" -> "https://www.energimyndigheten.se/klimat/transporter/laddinfrastruktur/registrera-din-laddstation/elbilsagare/"
                else -> "mailto:post@nobil.no?subject=" + Uri.encode("Regarding charging station " + chargerStationData.internationalId)
            },
            null,
            chargerStationData.ocpiId != null ||
                    chargerStationData.updated.isAfter(LocalDateTime.now().minusMonths(6)),
            null,
            if (chargerStationData.ownedBy != null) HtmlCompat.fromHtml(
                chargerStationData.ownedBy,
                HtmlCompat.FROM_HTML_MODE_COMPACT
            ).toString() else null,
            if (chargerStationData.userComment != null) HtmlCompat.fromHtml(
                chargerStationData.userComment,
                HtmlCompat.FROM_HTML_MODE_COMPACT
            ).toString() else null,
            null,
            if (chargerStationData.description != null) HtmlCompat.fromHtml(
                chargerStationData.description,
                HtmlCompat.FROM_HTML_MODE_COMPACT
            ).toString() else null,
            if (Regex("""\d+\.\w+""").matchEntire(chargerStationData.image) != null) listOf(
                NobilChargerPhotoAdapter(chargerStationData.image)
            ) else null,
            null,
            // 2: Availability
            chargerStationAttributes.st["2"]?.attrTrans,
            // 24: Open 24h
            if (chargerStationAttributes.st["24"]?.attrTrans == "Yes") OpeningHours(
                twentyfourSeven = true,
                null,
                null
            ) else null,
            Cost(
                // 7: Parking fee
                freeparking = when (chargerStationAttributes.st["7"]?.attrTrans) {
                    "Yes" -> false
                    "No" -> true
                    else -> null
                },
                descriptionLong = chargerStationAttributes.conn.mapNotNull {
                    // 19: Payment method
                    when (it.value["19"]?.attrValId) {
                        "1" -> listOf("Mobile phone") // TODO: Translate
                        "2" -> listOf("Bank card")
                        "10" -> listOf("Other")
                        "20" -> listOf("Mobile phone", "Charging card")
                        "21" -> listOf("Bank card", "Charging card")
                        "25" -> listOf("Bank card", "Charging card", "Mobile phone")
                        else -> null
                    }
                }.flatten().sorted().toSet().ifEmpty { null }
                    ?.joinToString(prefix = "Accepted payment methods: ")
            ),
            dataLicense,
            null,
            null,
            null,
            Instant.now(),
            true
        )

        val accessibilities = filters?.getMultipleChoiceValue("accessibilities")
        if (accessibilities != null && !accessibilities.all) {
            if (!accessibilities.values.contains(chargeLocation.accessibility)) return null
        }

        val freeparking = filters?.getBooleanValue("freeparking")
        if (freeparking == true && chargeLocation.cost?.freeparking != true) return null

        val open247 = filters?.getBooleanValue("open_247")
        if (open247 == true && chargeLocation.openinghours?.twentyfourSeven != true) return null

        return chargeLocation
    }

    companion object {
        fun createChargepointFromNobilConnection(attribs: Map<String, NobilChargerStationGenericAttribute>): Chargepoint? {
            // https://nobil.no/admin/attributes.php

            val isFixedCable = attribs["25"]?.attrTrans == "Yes"
            val connectionType = when (attribs["4"]?.attrValId) {
                "0" -> "" // Unspecified
                "30" -> Chargepoint.CHADEMO // CHAdeMO
                "31" -> Chargepoint.TYPE_1 // Type 1
                "32" -> if (isFixedCable) Chargepoint.TYPE_2_PLUG else Chargepoint.TYPE_2_SOCKET // Type 2
                "39" -> Chargepoint.CCS_UNKNOWN // CCS/Combo
                "40" -> Chargepoint.SUPERCHARGER // Tesla Connector Model
                "70" -> return null // Hydrogen
                "82" -> return null // Biogas
                "87" -> "" // MCS

                // These are deprecated and not used
                "50" -> "" // Type 2 + Schuko
                "60" -> "" // Type1/Type2

                else -> ""
            }

            val connectionPower = when (attribs["5"]?.attrValId) {
                "7" -> 3.6 // 3,6 kW - 230V 1-phase max 16A
                "8" -> 7.4 // 7,4 kW - 230V 1-phase max 32A
                "10" -> 11.0 // 11 kW - 400V 3-phase max 16A
                "11" -> 22.0 // 22 kW - 400V 3-phase max 32A
                "12" -> 43.0 // 43 kW - 400V 3-phase max 63A
                "13" -> 50.0 // 50 kW - 500VDC max 100A
                "16" -> 11.0 // 230V 3-phase max 16A'
                "17" -> 22.0 // 230V 3-phase max 32A
                "18" -> 43.0 // 230V 3-phase max 63A
                "19" -> 20.0 // 20 kW - 500VDC max 50A
                "22" -> 135.0 // 135 kW - 480VDC max 270A
                "23" -> 100.0 // 100 kW - 500VDC max 200A
                "24" -> 150.0 // 150 kW DC
                "25" -> 350.0 // 350 kW DC
                "26" -> null // 350 bar
                "27" -> null // 700 bar
                "29" -> 75.0 // 75 kW DC
                "30" -> 225.0 // 225 kW DC
                "31" -> 250.0 // 250 kW DC
                "32" -> 200.0 // 200 kW DC
                "33" -> 300.0 // 300 kW DC
                "34" -> null // CBG
                "35" -> null // LBG
                "36" -> 400.0 // 400 kW DC
                "37" -> 30.0 // 30 kW DC
                "38" -> 62.5 // 62,5 kW DC
                "39" -> 500.0 // 500 kW DC
                "41" -> 175.0 // 175 kW DC
                "42" -> 180.0 // 180 kW DC
                "43" -> 600.0 // 600 kW DC
                "44" -> 700.0 // 700 kW DC
                "45" -> 800.0 // 800 kW DC
                else -> null
            }

            val connectionVoltage = if (attribs["12"]?.attrVal is String) attribs["12"]?.attrVal.toString().toDoubleOrNull() else null
            val connectionCurrent = if (attribs["31"]?.attrVal is String) attribs["31"]?.attrVal.toString().toDoubleOrNull() else null
            val evseId = if (attribs["28"]?.attrVal is String) listOf(attribs["28"]?.attrVal.toString()) else null

            return Chargepoint(connectionType, connectionPower, 1, connectionCurrent, connectionVoltage, evseId)
        }
    }
}

@JsonClass(generateAdapter = true)
data class NobilChargerStationData(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "ocpidb_mapping_stasjon_id") val ocpiId: String?,
    @Json(name = "Street") val street: String?,
    @Json(name = "House_number") val houseNumber: String,
    @Json(name = "Zipcode") val zipCode: String?,
    @Json(name = "City") val city: String?,
    @Json(name = "Municipality_ID") val municipalityId: String,
    @Json(name = "Municipality") val municipality: String,
    @Json(name = "County_ID") val countyId: String,
    @Json(name = "County") val county: String,
    @Json(name = "Description_of_location") val description: String?,
    @Json(name = "Owned_by") val ownedBy: String?,
    @Json(name = "Operator") val operator: String?,
    @Json(name = "Number_charging_points") val numChargePoints: Int,
    @Json(name = "Position") val position: Coordinate,
    @Json(name = "Image") val image: String,
    @Json(name = "Available_charging_points") val availableChargePoints: Int,
    @Json(name = "User_comment") val userComment: String?,
    @Json(name = "Contact_info") val contactInfo: String?,
    @Json(name = "Created") val created: LocalDateTime,
    @Json(name = "Updated") val updated: LocalDateTime,
    @Json(name = "Station_status") val stationStatus: Int,
    @Json(name = "Land_code") val landCode: String,
    @Json(name = "International_id") val internationalId: String
)

@JsonClass(generateAdapter = true)
data class NobilChargerStationAttributes(
    @Json(name = "st") val st: Map<String, NobilChargerStationGenericAttribute>,
    @Json(name = "conn") val conn: Map<String, Map<String, NobilChargerStationGenericAttribute>>
)

@JsonClass(generateAdapter = true)
data class NobilChargerStationGenericAttribute(
    @Json(name = "attrtypeid") val attrTypeId: String,
    @Json(name = "attrname") val attrName: String,
    @Json(name = "attrvalid") val attrValId: String,
    @Json(name = "trans") val attrTrans: String,
    @Json(name = "attrval") val attrVal: Any
)

@Parcelize
@JsonClass(generateAdapter = true)
class NobilChargerPhotoAdapter(override val id: String) :
    ChargerPhoto(id) {
    override fun getUrl(height: Int?, width: Int?, size: Int?, allowOriginal: Boolean): String {
        val maxSize = size ?: max(height, width)
        return "https://www.nobil.no/img/ladestasjonbilder/" +
                when (maxSize) {
                    in 0..50 -> "tn_$id"
                    else -> id
                }
    }
}
