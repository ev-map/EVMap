package net.vonforst.evmap.api.openchargemap

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize
import net.vonforst.evmap.max
import net.vonforst.evmap.model.Address
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.model.ChargepriceData
import net.vonforst.evmap.model.ChargerPhoto
import net.vonforst.evmap.model.Coordinate
import net.vonforst.evmap.model.Cost
import net.vonforst.evmap.model.FaultReport
import net.vonforst.evmap.model.ReferenceData
import java.time.Instant
import java.time.ZonedDateTime

// Unknown, Currently Available, Currently In Use, Operational
val noFaultStatuses = listOf(0L, 10L, 20L, 50L)

// Temporarily Unavailable, Partly Operational, Not Operational, Planned For Future Date
val faultStatuses = listOf(30L, 75L, 100L, 150L)
val faultReportCommentType = 1000L

// Removed (Decommissioned), Removed (Duplicate Listing)
val removedStatuses = listOf(200L, 210L)

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
    @Json(name = "MediaItems") val mediaItems: List<OCMMediaItem>?,
    @Json(name = "StatusTypeID") val statusTypeId: Long?,
    @Json(name = "StatusType") val statusType: OCMStatusType?,
    @Json(name = "UserComments") val userComments: List<OCMUserComment>?,
    @Json(name = "DateLastStatusUpdate") val lastStatusUpdateDate: ZonedDateTime?
) {
    fun convert(refData: OCMReferenceData, isDetailed: Boolean) = ChargeLocation(
        id,
        "openchargemap",
        addressInfo.title,
        Coordinate(addressInfo.latitude, addressInfo.longitude),
        addressInfo.toAddress(refData),
        connections.map { it.convert(refData) },
        operatorInfo?.title ?: refData.operators.find { it.id == operatorId }?.title,
        "https://map.openchargemap.io/?id=$id",
        "https://map.openchargemap.io/?id=$id",
        convertFaultReport(),
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
            connections.map { "${it.connectionTypeId},${it.currentTypeId}" }),
        operatorInfo?.websiteUrl,
        if (operatorInfo?.websiteUrl?.withoutTrailingSlash() != addressInfo.relatedUrl?.withoutTrailingSlash()) addressInfo.relatedUrl else null,
        Instant.now(),
        isDetailed
    )

    private fun String.withoutTrailingSlash(): String {
        return this.replace(Regex("/$"), "")
    }

    private fun convertFaultReport(): FaultReport? {
        if (statusTypeId in faultStatuses || connections.any { it.statusTypeId in faultStatuses }) {
            if (userComments != null) {
                val comment = userComments.filter { it.commentTypeId == faultReportCommentType }
                    .maxByOrNull { it.dateCreated }
                if (comment != null) {
                    return FaultReport(comment.dateCreated.toInstant(), comment.comment ?: "")
                }
            }
            if (statusType != null && statusType.id in faultStatuses) {
                return FaultReport(lastStatusUpdateDate?.toInstant(), statusType.title)
            } else if (connections.any { it.statusType != null && it.statusTypeId in faultStatuses }) {
                return FaultReport(
                    lastStatusUpdateDate?.toInstant(),
                    connections.first { it.statusType != null && it.statusTypeId in faultStatuses }.statusType!!.title
                )
            }
            return FaultReport(null, "")
        } else {
            return null
        }
    }
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
    @Json(name = "Comments") val comments: String?,
    @Json(name = "StatusTypeID") val statusTypeId: Long?,
    @Json(name = "StatusType") val statusType: OCMStatusType?
) {
    fun convert(refData: OCMReferenceData) = Chargepoint(
        convertConnectionTypeFromOCM(connectionTypeId, refData),
        power,
        quantity ?: 1,
        voltage?.toDouble(),
        amps?.toDouble()
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
                27L -> Chargepoint.SUPERCHARGER  // Tesla North American plug (NACS)
                30L -> Chargepoint.SUPERCHARGER  // European Tesla Model S/X Supercharger plug (DC on Type 2)
                25L -> Chargepoint.TYPE_2_SOCKET
                1036L -> Chargepoint.TYPE_2_PLUG
                1L -> Chargepoint.TYPE_1
                36L -> Chargepoint.TYPE_3A
                26L -> Chargepoint.TYPE_3C
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

@JsonClass(generateAdapter = true)
data class OCMUserComment(
    @Json(name = "ID") val id: Long,
    @Json(name = "CommentTypeID") val commentTypeId: Long,
    @Json(name = "Comment") val comment: String?,
    @Json(name = "UserName") val userName: String?,
    @Json(name = "DateCreated") val dateCreated: ZonedDateTime
)

@JsonClass(generateAdapter = true)
data class OCMStatusType(
    @Json(name = "ID") val id: Long,
    @Json(name = "Title") val title: String
)

@Parcelize
@JsonClass(generateAdapter = true)
class OCMChargerPhotoAdapter(
    override val id: String,
    val largeUrl: String,
    val thumbUrl: String
) : ChargerPhoto(id) {
    override fun getUrl(height: Int?, width: Int?, size: Int?, allowOriginal: Boolean): String {
        val maxSize = size ?: max(height, width)
        val mediumUrl = thumbUrl.replace(".thmb.", ".medi.")
        return when (maxSize) {
            in 0..100 -> thumbUrl
            in 101..400 -> mediumUrl
            else -> if (allowOriginal) largeUrl else mediumUrl
        }
    }
}
