package net.vonforst.evmap.api.chargeprice

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Patterns
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import jsonapi.*
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.api.equivalentPlugTypes
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.ui.currency
import kotlin.math.ceil
import kotlin.math.floor


@Resource("charge_price_request")
@JsonClass(generateAdapter = true)
data class ChargepriceRequest(
    @Json(name = "data_adapter")
    val dataAdapter: String,
    val station: ChargepriceStation,
    val options: ChargepriceOptions,
    @ToMany("tariffs")
    val tariffs: List<ChargepriceTariff>? = null,
    @ToOne("vehicle")
    val vehicle: ChargepriceCar? = null,
    @RelationshipsObject var relationships: Relationships? = null
)

@JsonClass(generateAdapter = true)
data class ChargepriceStation(
    val longitude: Double,
    val latitude: Double,
    val country: String?,
    val network: String?,
    @Json(name = "charge_points") val chargePoints: List<ChargepriceChargepoint>
) {
    companion object {
        fun fromEvmap(
            charger: ChargeLocation,
            compatibleConnectors: List<String>,
        ): ChargepriceStation {
            if (charger.chargepriceData == null) throw IllegalArgumentException()

            val plugTypes =
                charger.chargepriceData.plugTypes ?: charger.chargepoints.map { it.type }
            return ChargepriceStation(
                charger.coordinates.lng,
                charger.coordinates.lat,
                charger.chargepriceData.country,
                charger.chargepriceData.network,
                charger.chargepoints.zip(plugTypes)
                    .filter { equivalentPlugTypes(it.first.type).any { it in compatibleConnectors } }
                    .map { ChargepriceChargepoint(it.first.power ?: 0.0, it.second) }
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class ChargepriceChargepoint(
    val power: Double,
    val plug: String
)

@JsonClass(generateAdapter = true)
data class ChargepriceOptions(
    @Json(name = "max_monthly_fees") val maxMonthlyFees: Double? = null,
    val energy: Double? = null,
    val duration: Int? = null,
    @Json(name = "battery_range") val batteryRange: List<Double>? = null,
    @Json(name = "car_ac_phases") val carAcPhases: Int? = null,
    val currency: String? = null,
    @Json(name = "start_time") val startTime: Int? = null,
    @Json(name = "allow_unbalanced_load") val allowUnbalancedLoad: Boolean? = null,
    @Json(name = "provider_customer_tariffs") val providerCustomerTariffs: Boolean? = null,
    @Json(name = "show_price_unavailable") val showPriceUnavailable: Boolean? = null,
    @Json(name = "show_all_brand_restricted_tariffs") val showAllBrandRestrictedTariffs: Boolean? = null
)

@Resource("tariff")
@Parcelize
@JsonClass(generateAdapter = true)
data class ChargepriceTariff(
    @Id val id_: String?,
    val provider: String,
    val name: String,
    @Json(name = "direct_payment")
    val directPayment: Boolean = false,
    @Json(name = "provider_customer_tariff")
    val providerCustomerTariff: Boolean = false,
    @Json(name = "supported_countries")
    val supportedCountries: Set<String>,
    @Json(name = "charge_card_id")
    val chargeCardId: String?,  // GE charge card ID
) : Parcelable {
    val id: String
        get() = id_!!
}

@JsonClass(generateAdapter = true)
@Resource("car")
@Parcelize
data class ChargepriceCar(
    @Id val id_: String?,
    val name: String,
    val brand: String,

    @Json(name = "dc_charge_ports")
    val dcChargePorts: List<String>,

    @Json(name = "usable_battery_size")
    val usableBatterySize: Float,

    @Json(name = "ac_max_power")
    val acMaxPower: Float,

    @Json(name = "dc_max_power")
    val dcMaxPower: Float?
) : Equatable, Parcelable {
    fun formatSpecs(): String = buildString {
        append("%.0f kWh".format(usableBatterySize))
        append(" | ")
        append("AC %.0f kW".format(acMaxPower))
        dcMaxPower?.let {
            append(" | ")
            append("DC %.0f kW".format(it))
        }
    }

    companion object {
        private val acConnectors = listOf(
            Chargepoint.CEE_BLAU,
            Chargepoint.CEE_ROT,
            Chargepoint.SCHUKO,
            Chargepoint.TYPE_1,
            Chargepoint.TYPE_2_UNKNOWN,
            Chargepoint.TYPE_2_SOCKET,
            Chargepoint.TYPE_2_PLUG
        )
        private val plugMapping = mapOf(
            "ccs" to Chargepoint.CCS_UNKNOWN,
            "tesla_suc" to Chargepoint.SUPERCHARGER,
            "tesla_ccs" to Chargepoint.CCS_UNKNOWN,
            "chademo" to Chargepoint.CHADEMO
        )
    }

    val id: String
        get() = id_!!

    val compatibleEvmapConnectors: List<String>
        get() = dcChargePorts.map {
            plugMapping[it]
        }.filterNotNull().plus(acConnectors)
}

@JsonClass(generateAdapter = true)
@Resource("brand")
@Parcelize
data class ChargepriceBrand(
    @Id val id: String?
) : Parcelable

@JsonClass(generateAdapter = true)
@Resource("charge_price")
@Parcelize
data class ChargePrice(
    val provider: String,
    @Json(name = "tariff_name")
    val tariffName: String,
    val url: String,
    @Json(name = "monthly_min_sales")
    val monthlyMinSales: Double = 0.0,
    @Json(name = "total_monthly_fee")
    val totalMonthlyFee: Double = 0.0,
    @Json(name = "flat_rate")
    val flatRate: Boolean = false,

    @Json(name = "direct_payment")
    val directPayment: Boolean = false,

    @Json(name = "provider_customer_tariff")
    val providerCustomerTariff: Boolean = false,
    val currency: String,

    @Json(name = "start_time")
    val startTime: Int = 0,
    val tags: List<ChargepriceTag>,

    @Json(name = "charge_point_prices")
    val chargepointPrices: List<ChargepointPrice>,

    @Json(name = "branding")
    val branding: ChargepriceBranding? = null,

    @RelationshipsObject
    val relationships: @WriteWith<RelationshipsParceler>() Relationships? = null,
) : Equatable, Cloneable, Parcelable {
    val tariffId: String?
        get() = (relationships?.get("tariff") as? Relationship.ToOne)?.data?.id

    fun formatMonthlyFees(ctx: Context): String {
        return listOfNotNull(
            if (totalMonthlyFee > 0) {
                ctx.getString(R.string.chargeprice_base_fee, totalMonthlyFee, currency(currency))
            } else null,
            if (monthlyMinSales > 0) {
                ctx.getString(R.string.chargeprice_min_spend, monthlyMinSales, currency(currency))
            } else null
        ).joinToString(", ")
    }
}

/**
 * Parceler implementation for the Relationships object.
 * Note that this ignores certain fields that we don't need (links, meta, etc.)
 */
internal object RelationshipsParceler : Parceler<Relationships?> {
    override fun create(parcel: Parcel): Relationships? {
        if (parcel.readInt() == 0) return null

        val nMembers = parcel.readInt()
        val members = (0 until nMembers).map { _ ->
            val key = parcel.readString()!!
            val value = if (parcel.readInt() == 0) {
                val type = parcel.readString()
                val id = parcel.readString()
                val ri = if (type != null && id != null) {
                    ResourceIdentifier(type, id)
                } else null
                Relationship.ToOne(ri)
            } else {
                val size = parcel.readInt()
                val ris = (0 until size).map { _ ->
                    val type = parcel.readString()!!
                    val id = parcel.readString()!!
                    ResourceIdentifier(type, id)
                }
                Relationship.ToMany(ris)
            }
            key to value
        }.toMap()

        return Relationships(members)
    }

    override fun Relationships?.write(parcel: Parcel, flags: Int) {
        if (this == null) {
            parcel.writeInt(0)
            return
        } else {
            parcel.writeInt(1)
        }

        parcel.writeInt(members.size)
        for (member in this.members) {
            parcel.writeString(member.key)
            when (val value = member.value) {
                is Relationship.ToOne -> {
                    parcel.writeInt(0)
                    parcel.writeString(value.data?.type)
                    parcel.writeString(value.data?.id)
                }
                is Relationship.ToMany -> {
                    parcel.writeInt(1)
                    parcel.writeInt(value.data.size)
                    for (ri in value.data) {
                        parcel.writeString(ri.type)
                        parcel.writeString(ri.id)
                    }
                }
            }
        }

    }
}

@JsonClass(generateAdapter = true)
@Parcelize
data class ChargepointPrice(
    val power: Double,
    val plug: String,
    val price: Double?,
    @Json(name = "price_distribution") val priceDistribution: PriceDistribution,
    @Json(name = "blocking_fee_start") val blockingFeeStart: Int?,
    @Json(name = "no_price_reason") var noPriceReason: String?
) : Parcelable {
    fun formatDistribution(ctx: Context): String {
        fun percent(value: Double): String {
            return ctx.getString(R.string.percent_format, value * 100) + "\u00a0"
        }

        fun time(value: Int): String {
            val h = floor(value.toDouble() / 60).toInt();
            val min = ceil(value.toDouble() % 60).toInt();
            if (h == 0 && min > 0) return "${min}min";
            // be slightly sloppy (3:01 is shown as 3h) to save space
            else if (h > 0 && (min == 0 || min == 1)) return "${h}h";
            else return "%d:%02dh".format(h, min);
        }

        // based on https://github.com/chargeprice/chargeprice-client/blob/d420bb2f216d9ad91a210a36dd0859a368a8229a/src/views/priceList.js
        with(priceDistribution) {
            return listOfNotNull(
                if (session != null && session > 0.0) {
                    (if (session < 1) percent(session) else "") + ctx.getString(R.string.chargeprice_session_fee)
                } else null,
                if (kwh != null && kwh > 0.0 && !isOnlyKwh) {
                    (if (kwh < 1) percent(kwh) else "") + ctx.getString(R.string.chargeprice_per_kwh)
                } else null,
                if (minute != null && minute > 0.0) {
                    (if (minute < 1) percent(minute) else "") + ctx.getString(R.string.chargeprice_per_minute) +
                            if (blockingFeeStart != null) {
                                " (${
                                    ctx.getString(
                                        R.string.chargeprice_blocking_fee,
                                        time(blockingFeeStart)
                                    )
                                })"
                            } else ""
                } else null,
                if ((minute == null || minute == 0.0) && blockingFeeStart != null) {
                    ctx.getString(R.string.chargeprice_blocking_fee, time(blockingFeeStart))
                } else null
            ).joinToString(" +\u00a0")
        }
    }
}

@JsonClass(generateAdapter = true)
@Parcelize
data class ChargepriceBranding(
    @Json(name = "background_color") val backgroundColor: String,
    @Json(name = "text_color") val textColor: String,
    @Json(name = "logo_url") val logoUrl: String
) : Parcelable

@JsonClass(generateAdapter = true)
@Parcelize
data class PriceDistribution(val kwh: Double?, val session: Double?, val minute: Double?) :
    Parcelable {
    val isOnlyKwh
        get() = kwh != null && kwh > 0 && (session == null || session == 0.0) && (minute == null || minute == 0.0)
}

@JsonClass(generateAdapter = true)
@Parcelize
data class ChargepriceTag(val kind: String, val text: String, val url: String?) : Equatable,
    Parcelable

@JsonClass(generateAdapter = true)
data class ChargepriceMeta(
    @Json(name = "charge_points") val chargePoints: List<ChargepriceChargepointMeta>
)

enum class ChargepriceInclude {
    @Json(name = "filter")
    FILTER,
    @Json(name = "always")
    ALWAYS,
    @Json(name = "exclusive")
    EXCLUSIVE
}

@JsonClass(generateAdapter = true)
@Parcelize
data class ChargepriceRequestTariffMeta(
    val include: ChargepriceInclude
) : Parcelable

@JsonClass(generateAdapter = true)
data class ChargepriceChargepointMeta(
    val power: Double,
    val plug: String,
    val energy: Double,
    val duration: Double
)

@Resource("user_feedback")
sealed class ChargepriceUserFeedback(
    val notes: String,
    val email: String,
    val context: String,
    val language: String
) {
    init {
        if (email.isBlank() || email.length > 100 || !Patterns.EMAIL_ADDRESS.matcher(email)
                .matches()
        ) {
            throw IllegalArgumentException("invalid email")
        }
        if (!ChargepriceApi.supportedLanguages.contains(language)) {
            throw IllegalArgumentException("invalid language")
        }
        if (context.length > 500) throw IllegalArgumentException("invalid context")
        if (notes.length > 1000) throw IllegalArgumentException("invalid notes")
    }
}

@JsonClass(generateAdapter = true)
@Resource(type = "missing_price")
class ChargepriceMissingPriceFeedback(
    val tariff: String,
    val cpo: String,
    val price: String,
    @Json(name = "poi_link") val poiLink: String,
    notes: String,
    email: String,
    context: String,
    language: String
) : ChargepriceUserFeedback(notes, email, context, language) {
    init {
        if (tariff.isBlank() || tariff.length > 100) throw IllegalArgumentException("invalid tariff")
        if (cpo.length > 200) throw IllegalArgumentException("invalid cpo")
        if (price.isBlank() || price.length > 100) throw IllegalArgumentException("invalid price")
        if (poiLink.isBlank() || poiLink.length > 200) throw IllegalArgumentException("invalid poiLink")
    }
}


@JsonClass(generateAdapter = true)
@Resource(type = "wrong_price")
class ChargepriceWrongPriceFeedback(
    val tariff: String,
    val cpo: String,
    @Json(name = "displayed_price") val displayedPrice: String,
    @Json(name = "actual_price") val actualPrice: String,
    @Json(name = "poi_link") val poiLink: String,
    notes: String,
    email: String,
    context: String,
    language: String,
) : ChargepriceUserFeedback(notes, email, context, language) {
    init {
        if (tariff.length > 100) throw IllegalArgumentException("invalid tariff")
        if (cpo.length > 200) throw IllegalArgumentException("invalid cpo")
        if (displayedPrice.length > 100) throw IllegalArgumentException("invalid displayedPrice")
        if (actualPrice.length > 100) throw IllegalArgumentException("invalid actualPrice")
        if (poiLink.length > 200) throw IllegalArgumentException("invalid poiLink")
    }
}


@JsonClass(generateAdapter = true)
@Resource(type = "missing_vehicle")
class ChargepriceMissingVehicleFeedback(
    val brand: String,
    val model: String,
    notes: String,
    email: String,
    context: String,
    language: String,
) : ChargepriceUserFeedback(notes, email, context, language) {
    init {
        if (brand.length > 100) throw IllegalArgumentException("invalid brand")
        if (model.length > 100) throw IllegalArgumentException("invalid model")
    }
}