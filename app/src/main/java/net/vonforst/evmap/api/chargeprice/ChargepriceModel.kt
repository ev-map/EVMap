package net.vonforst.evmap.api.chargeprice

import android.content.Context
import com.squareup.moshi.Json
import moe.banana.jsonapi2.HasMany
import moe.banana.jsonapi2.HasOne

import moe.banana.jsonapi2.JsonApi
import moe.banana.jsonapi2.Resource
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.ui.currency
import kotlin.math.ceil
import kotlin.math.floor


@JsonApi(type = "charge_price_request")
class ChargepriceRequest : Resource() {
    @field:Json(name = "data_adapter")
    lateinit var dataAdapter: String
    lateinit var station: ChargepriceStation
    lateinit var options: ChargepriceOptions
    var tariffs: HasMany<ChargepriceTariff>? = null
    var vehicle: HasOne<ChargepriceCar>? = null
}

data class ChargepriceStation(
    val longitude: Double,
    val latitude: Double,
    val country: String?,
    val network: String?,
    @Json(name = "charge_points") val chargePoints: List<ChargepriceChargepoint>
) {
    companion object {
        fun fromGoingelectric(
            geCharger: ChargeLocation,
            compatibleConnectors: List<String>
        ): ChargepriceStation {
            return ChargepriceStation(
                geCharger.coordinates.lng,
                geCharger.coordinates.lat,
                geCharger.address.country,
                geCharger.network,
                geCharger.chargepoints.filter {
                    it.type in compatibleConnectors
                }.map {
                    ChargepriceChargepoint(it.power, it.type)
                }
            )
        }
    }
}

data class ChargepriceChargepoint(
    val power: Double,
    val plug: String
)

data class ChargepriceOptions(
    @Json(name = "max_monthly_fees") val maxMonthlyFees: Double? = null,
    val energy: Double? = null,
    val duration: Int? = null,
    @Json(name = "battery_range") val batteryRange: List<Double>? = null,
    @Json(name = "car_ac_phases") val carAcPhases: Int? = null,
    val currency: String? = null,
    @Json(name = "start_time") val startTime: Int? = null,
    @Json(name = "allow_unbalanced_load") val allowUnbalancedLoad: Boolean? = null,
    @Json(name = "provider_customer_tariffs") val providerCustomerTariffs: Boolean? = null
)

@JsonApi(type = "tariff")
class ChargepriceTariff() : Resource() {
    lateinit var provider: String
    lateinit var name: String
    @field:Json(name = "direct_payment")
    var directPayment: Boolean = false
    @field:Json(name = "provider_customer_tariff")
    var providerCustomerTariff: Boolean = false
    @field:Json(name = "supported_cuntries")
    lateinit var supportedCountries: Set<String>
    @field:Json(name = "charge_card_id")
    lateinit var chargeCardId: String  // GE charge card ID

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ChargepriceTariff

        if (provider != other.provider) return false
        if (name != other.name) return false
        if (directPayment != other.directPayment) return false
        if (providerCustomerTariff != other.providerCustomerTariff) return false
        if (supportedCountries != other.supportedCountries) return false
        if (chargeCardId != other.chargeCardId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + provider.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + directPayment.hashCode()
        result = 31 * result + providerCustomerTariff.hashCode()
        result = 31 * result + supportedCountries.hashCode()
        result = 31 * result + chargeCardId.hashCode()
        return result
    }
}

@JsonApi(type = "car")
class ChargepriceCar : Resource() {
    lateinit var name: String
    lateinit var brand: String

    @field:Json(name = "dc_charge_ports")
    lateinit var dcChargePorts: List<String>
    lateinit var manufacturer: HasOne<ChargepriceBrand>

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ChargepriceCar

        if (name != other.name) return false
        if (brand != other.brand) return false
        if (dcChargePorts != other.dcChargePorts) return false
        if (manufacturer != other.manufacturer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + brand.hashCode()
        result = 31 * result + dcChargePorts.hashCode()
        result = 31 * result + manufacturer.hashCode()
        return result
    }
}

@JsonApi(type = "brand")
class ChargepriceBrand : Resource()

@JsonApi(type = "charge_price")
class ChargePrice : Resource(), Equatable, Cloneable {
    lateinit var provider: String

    @field:Json(name = "tariff_name")
    lateinit var tariffName: String
    lateinit var url: String

    @field:Json(name = "monthly_min_sales")
    var monthlyMinSales: Double = 0.0

    @field:Json(name = "total_monthly_fee")
    var totalMonthlyFee: Double = 0.0

    @field:Json(name = "flat_rate")
    var flatRate: Boolean = false

    @field:Json(name = "direct_payment")
    var directPayment: Boolean = false

    @field:Json(name = "provider_customer_tariff")
    var providerCustomerTariff: Boolean = false
    lateinit var currency: String

    @field:Json(name = "start_time")
    var startTime: Int = 0
    lateinit var tags: List<ChargepriceTag>

    @field:Json(name = "charge_point_prices")
    lateinit var chargepointPrices: List<ChargepointPrice>


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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ChargePrice

        if (provider != other.provider) return false
        if (tariffName != other.tariffName) return false
        if (url != other.url) return false
        if (monthlyMinSales != other.monthlyMinSales) return false
        if (totalMonthlyFee != other.totalMonthlyFee) return false
        if (flatRate != other.flatRate) return false
        if (directPayment != other.directPayment) return false
        if (providerCustomerTariff != other.providerCustomerTariff) return false
        if (currency != other.currency) return false
        if (startTime != other.startTime) return false
        if (tags != other.tags) return false
        if (chargepointPrices != other.chargepointPrices) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + provider.hashCode()
        result = 31 * result + tariffName.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + monthlyMinSales.hashCode()
        result = 31 * result + totalMonthlyFee.hashCode()
        result = 31 * result + flatRate.hashCode()
        result = 31 * result + directPayment.hashCode()
        result = 31 * result + providerCustomerTariff.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + startTime
        result = 31 * result + tags.hashCode()
        result = 31 * result + chargepointPrices.hashCode()
        return result
    }

    public override fun clone(): ChargePrice {
        return ChargePrice().apply {
            chargepointPrices = this@ChargePrice.chargepointPrices
            currency = this@ChargePrice.currency
            directPayment = this@ChargePrice.directPayment
            flatRate = this@ChargePrice.flatRate
            monthlyMinSales = this@ChargePrice.monthlyMinSales
            provider = this@ChargePrice.provider
            providerCustomerTariff = this@ChargePrice.providerCustomerTariff
            startTime = this@ChargePrice.startTime
            tags = this@ChargePrice.tags
            tariffName = this@ChargePrice.tariffName
            totalMonthlyFee = this@ChargePrice.totalMonthlyFee
            url = this@ChargePrice.url
        }
    }
}

data class ChargepointPrice(
    val power: Double,
    val plug: String,
    val price: Double,
    @Json(name = "price_distribution") val priceDistribution: PriceDistribution,
    @Json(name = "blocking_fee_start") val blockingFeeStart: Int?,
    @Json(name = "no_price_reason") var noPriceReason: String?
) {
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

data class PriceDistribution(val kwh: Double?, val session: Double?, val minute: Double?) {
    val isOnlyKwh =
        kwh != null && kwh > 0 && (session == null || session == 0.0) && (minute == null || minute == 0.0)
}

data class ChargepriceTag(val kind: String, val text: String, val url: String?) : Equatable

data class ChargepriceMeta(
    @Json(name = "charge_points") val chargePoints: List<ChargepriceChargepointMeta>
)

data class ChargepriceChargepointMeta(
    val power: Double,
    val plug: String,
    val energy: Double,
    val duration: Double
)