package net.vonforst.evmap.api.openchargemap

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import net.vonforst.evmap.max
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
    @Json(name = "NumberOfPoints") val numPoints: Int?,
    @Json(name = "GeneralComments") val generalComments: String?,
    @Json(name = "OperatorInfo") val operatorInfo: OCMOperator?,
    @Json(name = "OperatorID") val operatorId: Long?,
    @Json(name = "DataProvider") val dataProvider: OCMDataProvider?,
    @Json(name = "MediaItems") val mediaItems: List<OCMMediaItem>?
) {
    fun convert(refData: OCMReferenceData) = ChargeLocation(
        id,
        addressInfo.title,
        Coordinate(addressInfo.latitude, addressInfo.longitude),
        addressInfo.toAddress(refData),
        connections.map { it.convert(refData) },
        operatorInfo?.title,
        "https://openchargemap.org/site/poi/details/$id",
        "https://openchargemap.org/site/poi/edit/$id",
        null,
        recentlyVerified,
        null,
        null,
        generalComments,
        null,
        addressInfo.accessComments,
        mediaItems?.mapNotNull { it.convert() },
        null,
        null,
        cost?.let { Cost(descriptionShort = it) },
        dataProvider?.let { "© ${it.title}" + if (it.license != null) ". ${it.license}" else "" },
        ChargepriceData(
            addressInfo.countryISOCode(refData),
            operatorId?.toString(),
            connections.map { "${it.connectionTypeId},${it.currentTypeId}" })
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
        refData.countries.find { it.id == countryId }?.title,
        postcode,
        listOfNotNull(addressLine1, addressLine2).joinToString(", ")
    )

    fun countryISOCode(refData: OCMReferenceData) =
        refData.countries.find { it.id == countryId }?.isoCode
}

@JsonClass(generateAdapter = true)
data class OCMConnection(
    @Json(name = "ConnectionTypeID") val connectionTypeId: Long,
    @Json(name = "CurrentTypeID") val currentTypeId: Long?,
    @Json(name = "Amps") val amps: Int?,
    @Json(name = "Voltage") val voltage: Int?,
    @Json(name = "PowerKW") val power: Double?,
    @Json(name = "Quantity") val quantity: Int?,
    @Json(name = "Comments") val comments: String?
) {
    fun convert(refData: OCMReferenceData) = Chargepoint(
        convertConnectionTypeFromOCM(connectionTypeId, refData),
        power ?: 0.0,
        quantity ?: 0
    )

    companion object {
        fun convertConnectionTypeFromOCM(id: Long, refData: OCMReferenceData): String {
            val title = refData.connectionTypes.find { it.id == id }?.title
            return when (id) {
                32L -> Chargepoint.CCS_TYPE_1
                33L -> Chargepoint.CCS_TYPE_2
                2L -> Chargepoint.CHADEMO
                16L -> Chargepoint.CEE_BLAU
                17L -> Chargepoint.CEE_ROT
                28L -> Chargepoint.SCHUKO
                8L -> Chargepoint.TESLA_ROADSTER_HPC
                27L -> Chargepoint.SUPERCHARGER
                25L -> Chargepoint.TYPE_2_SOCKET
                1036L -> Chargepoint.TYPE_2_PLUG
                1L -> Chargepoint.TYPE_1
                36L -> Chargepoint.TYPE_3
                26L -> Chargepoint.TYPE_3
                else -> title ?: ""
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class OCMReferenceData(
    @Json(name = "ConnectionTypes") val connectionTypes: List<OCMConnectionType>,
    @Json(name = "Countries") val countries: List<OCMCountry>,
    @Json(name = "Operators") val operators: List<OCMOperator>
) : ReferenceData()

@JsonClass(generateAdapter = true)
@Entity
data class OCMConnectionType(
    @Json(name = "ID") @PrimaryKey val id: Long,
    @Json(name = "Title") val title: String,
    @Json(name = "FormalName") val formalName: String?,
    @Json(name = "IsDiscontinued") val discontinued: Boolean?,
    @Json(name = "IsObsolete") val obsolete: Boolean?
)

@JsonClass(generateAdapter = true)
@Entity
data class OCMCountry(
    @Json(name = "ID") @PrimaryKey val id: Long,
    @Json(name = "ISOCode") val isoCode: String,
    @Json(name = "ContinentCode") val continentCode: String?,
    @Json(name = "Title") val title: String
)

@JsonClass(generateAdapter = true)
data class OCMDataProvider(
    @Json(name = "ID") val id: Long,
    @Json(name = "WebsiteURL") val websiteUrl: String?,
    @Json(name = "Title") val title: String,
    @Json(name = "License") val license: String?
)

@JsonClass(generateAdapter = true)
@Entity
data class OCMOperator(
    @Json(name = "ID") @PrimaryKey val id: Long,
    @Json(name = "WebsiteURL") val websiteUrl: String?,
    @Json(name = "Title") val title: String,
    @Json(name = "ContactEmail") val contactEmail: String?,
    @Json(name = "PhonePrimaryContact") val contactTelephone1: String?,
    @Json(name = "PhoneSecondaryContact") val contactTelephone2: String?,
)

@JsonClass(generateAdapter = true)
data class OCMMediaItem(
    @Json(name = "ID") val id: Long,
    @Json(name = "ItemURL") val url: String,
    @Json(name = "ItemThumbnailURL") val thumbUrl: String,
    @Json(name = "IsVideo") val isVideo: Boolean,
    @Json(name = "IsExternalResource") val isExternalResource: Boolean,
    @Json(name = "Comment") val comment: String?
) {
    fun convert(): ChargerPhoto? {
        if (isVideo or isExternalResource) return null

        return OCMChargerPhotoAdapter(id.toString(), url, thumbUrl)
    }
}

@Parcelize
private class OCMChargerPhotoAdapter(
    override val id: String,
    private val largeUrl: String,
    private val thumbUrl: String
) : ChargerPhoto(id) {
    override fun getUrl(height: Int?, width: Int?, size: Int?): String {
        val maxSize = size ?: max(height, width)
        val mediumUrl = thumbUrl.replace(".thmb.", ".medi.")
        return when (maxSize) {
            0 -> mediumUrl
            in 1..100 -> thumbUrl
            in 101..400 -> mediumUrl
            else -> largeUrl
        }
    }
}