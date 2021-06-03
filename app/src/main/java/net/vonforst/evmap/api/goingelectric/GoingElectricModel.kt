package net.vonforst.evmap.api.goingelectric

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import net.vonforst.evmap.model.*
import java.time.Instant
import java.time.LocalTime

@JsonClass(generateAdapter = true)
data class GEChargepointList(
    val status: String,
    val chargelocations: List<GEChargepointListItem>,
    @JsonObjectOrFalse val startkey: Int?
)

@JsonClass(generateAdapter = true)
data class GEStringList(
    val status: String,
    val result: List<String>
)

@JsonClass(generateAdapter = true)
data class GEChargeCardList(
    val status: String,
    val result: List<GEChargeCard>
)

sealed class GEChargepointListItem {
    abstract fun convert(apikey: String): ChargepointListItem
}

@JsonClass(generateAdapter = true)
data class GEChargeLocation(
    @Json(name = "ge_id") val id: Long,
    val name: String,
    val coordinates: GECoordinate,
    val address: GEAddress,
    val chargepoints: List<GEChargepoint>,
    @JsonObjectOrFalse val network: String?,
    val url: String,
    @JsonObjectOrFalse @Json(name = "fault_report") val faultReport: GEFaultReport?,
    val verified: Boolean,
    @Json(name = "barrierfree") val barrierFree: Boolean?,
    // only shown in details:
    @JsonObjectOrFalse val operator: String?,
    @JsonObjectOrFalse @Json(name = "general_information") val generalInformation: String?,
    @JsonObjectOrFalse @Json(name = "ladeweile") val amenities: String?,
    @JsonObjectOrFalse @Json(name = "location_description") val locationDescription: String?,
    val photos: List<GEChargerPhoto>?,
    @JsonObjectOrFalse val chargecards: List<GEChargeCardId>?,
    val openinghours: GEOpeningHours?,
    val cost: GECost?
) : GEChargepointListItem() {
    override fun convert(apikey: String): ChargeLocation {
        return ChargeLocation(
            id,
            name,
            coordinates.convert(),
            address.convert(),
            chargepoints.map { it.convert() },
            network,
            url,
            faultReport?.convert(),
            verified,
            barrierFree,
            operator,
            generalInformation,
            amenities,
            locationDescription,
            photos?.map { it.convert(apikey) },
            chargecards?.map { it.convert() },
            openinghours?.convert(),
            cost?.convert()
        )
    }
}

@JsonClass(generateAdapter = true)
data class GECost(
    val freecharging: Boolean,
    val freeparking: Boolean,
    @JsonObjectOrFalse @Json(name = "description_short") val descriptionShort: String?,
    @JsonObjectOrFalse @Json(name = "description_long") val descriptionLong: String?
) {
    fun convert() = Cost(freecharging, freeparking, descriptionShort, descriptionLong)
}

@JsonClass(generateAdapter = true)
data class GEOpeningHours(
    @Json(name = "24/7") val twentyfourSeven: Boolean,
    @JsonObjectOrFalse val description: String?,
    val days: GEOpeningHoursDays?
) {
    fun convert() = OpeningHours(twentyfourSeven, description, days?.convert())
}

@JsonClass(generateAdapter = true)
data class GEOpeningHoursDays(
    val monday: GEHours,
    val tuesday: GEHours,
    val wednesday: GEHours,
    val thursday: GEHours,
    val friday: GEHours,
    val saturday: GEHours,
    val sunday: GEHours,
    val holiday: GEHours
) {
    fun convert() = OpeningHoursDays(
        monday.convert(),
        tuesday.convert(),
        wednesday.convert(),
        thursday.convert(),
        friday.convert(),
        saturday.convert(),
        sunday.convert(),
        holiday.convert()
    )
}

data class GEHours(
    val start: LocalTime?,
    val end: LocalTime?
) {
    fun convert() = Hours(start, end)
}

@JsonClass(generateAdapter = true)
data class GEChargerPhoto(val id: String) {
    fun convert(apikey: String): ChargerPhoto = GEChargerPhotoAdapter(id, apikey)
}

@Parcelize
private class GEChargerPhotoAdapter(override val id: String, private val apikey: String) :
    ChargerPhoto(id) {
    override fun getUrl(height: Int?, width: Int?, size: Int?): String {
        return "https://api.goingelectric.de/chargepoints/photo/?key=${apikey}&id=$id" +
                when {
                    size != null -> "&size=$size"
                    height != null -> "&height=$height"
                    width != null -> "&width=$width"
                    else -> ""
                }
    }
}

@JsonClass(generateAdapter = true)
data class GEChargeLocationCluster(
    val clusterCount: Int,
    val coordinates: GECoordinate
) : GEChargepointListItem() {
    override fun convert(apikey: String) =
        ChargeLocationCluster(clusterCount, coordinates.convert())
}

@JsonClass(generateAdapter = true)
data class GECoordinate(val lat: Double, val lng: Double) {
    fun convert() = Coordinate(lat, lng)
}

@JsonClass(generateAdapter = true)
data class GEAddress(
    @JsonObjectOrFalse val city: String?,
    @JsonObjectOrFalse val country: String?,
    @JsonObjectOrFalse val postcode: String?,
    @JsonObjectOrFalse val street: String?
) {
    fun convert() = Address(city, country, postcode, street)
}

@JsonClass(generateAdapter = true)
data class GEChargepoint(val type: String, val power: Double, val count: Int) {
    fun convert() = Chargepoint(type, power, count)
}

@JsonClass(generateAdapter = true)
data class GEFaultReport(val created: Instant?, val description: String?) {
    fun convert() = FaultReport(created, description)
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "ChargeCard")
data class GEChargeCard(
    @Json(name = "card_id") @PrimaryKey val id: Long,
    val name: String,
    val url: String
) {
    fun convert() = ChargeCard(id, name, url)
}

@JsonClass(generateAdapter = true)
data class GEChargeCardId(
    val id: Long
) {
    fun convert() = ChargeCardId(id)
}

data class GEReferenceData(
    val plugs: List<String>,
    val networks: List<String>,
    val chargecards: List<GEChargeCard>
) : ReferenceData()