package net.vonforst.evmap.adapter

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.StyleSpan
import androidx.core.text.HtmlCompat
import androidx.core.text.buildSpannedString
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.tesla.Pricing
import net.vonforst.evmap.api.availability.tesla.Rates
import net.vonforst.evmap.api.availability.tesla.TeslaChargingOwnershipGraphQlApi
import net.vonforst.evmap.bold
import net.vonforst.evmap.joinToSpannedString
import net.vonforst.evmap.model.ChargeCard
import net.vonforst.evmap.model.ChargeCardId
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Coordinate
import net.vonforst.evmap.model.OpeningHoursDays
import net.vonforst.evmap.plus
import net.vonforst.evmap.ui.currency
import net.vonforst.evmap.utils.formatDMS
import net.vonforst.evmap.utils.formatDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class DetailsAdapter : DataBindingAdapter<DetailsAdapter.Detail>() {
    data class Detail(
        val icon: Int,
        val contentDescription: Int,
        val text: CharSequence?,
        val detailText: CharSequence? = null,
        val links: Boolean = true,
        val clickable: Boolean = false,
        val hoursDays: OpeningHoursDays? = null
    ) : Equatable

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        if (item.hoursDays != null) {
            return R.layout.item_detail_openinghours
        } else {
            return R.layout.item_detail
        }
    }
}

fun buildDetails(
    loc: ChargeLocation?,
    chargeCards: Map<Long, ChargeCard>?,
    filteredChargeCards: Set<Long>?,
    teslaPricing: Pricing?,
    ctx: Context
): List<DetailsAdapter.Detail> {
    if (loc == null) return emptyList()

    return listOfNotNull(
        if (teslaPricing != null) DetailsAdapter.Detail(
            R.drawable.ic_tesla,
            R.string.cost,
            formatTeslaPricing(teslaPricing, ctx),
            formatTeslaParkingFee(teslaPricing, ctx)
        ) else null,
        if (loc.address != null) DetailsAdapter.Detail(
            R.drawable.ic_address,
            R.string.address,
            loc.address.toString(),
            loc.locationDescription,
            clickable = true
        ) else null,
        if (loc.operator != null) DetailsAdapter.Detail(
            R.drawable.ic_operator,
            R.string.operator,
            loc.operator
        ) else null,
        if (loc.network != null) DetailsAdapter.Detail(
            R.drawable.ic_network,
            R.string.network,
            loc.network,
            clickable = loc.networkUrl != null
        ) else null,
        if (loc.faultReport != null) DetailsAdapter.Detail(
            R.drawable.ic_fault_report,
            R.string.fault_report,
            loc.faultReport.created?.let {
                ctx.getString(
                    R.string.fault_report_date,
                    loc.faultReport.created
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                )
            } ?: "",
            loc.faultReport.description?.let {
                HtmlCompat.fromHtml(it.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY)
            } ?: "",
            clickable = true
        ) else null,
        if (loc.openinghours != null && !loc.openinghours.isEmpty) DetailsAdapter.Detail(
            R.drawable.ic_hours,
            R.string.hours,
            if (loc.openinghours.days != null || loc.openinghours.twentyfourSeven)
                loc.openinghours.getStatusText(ctx)
            else
                loc.openinghours.description ?: "",
            if (loc.openinghours.days != null || loc.openinghours.twentyfourSeven) loc.openinghours.description else null,
            hoursDays = loc.openinghours.days
        ) else null,
        if (loc.cost != null && !loc.cost.isEmpty) DetailsAdapter.Detail(
            R.drawable.ic_cost,
            R.string.cost,
            loc.cost.getStatusText(ctx),
            loc.cost.getDetailText()
        )
        else null,
        if (loc.chargecards != null && loc.chargecards.isNotEmpty() || loc.barrierFree == true)
            DetailsAdapter.Detail(
                R.drawable.ic_payment,
                R.string.charge_cards,
                listOfNotNull(
                    if (loc.barrierFree == true) ctx.resources.getString(R.string.charging_barrierfree) else null,
                    if (loc.chargecards != null && loc.chargecards.isNotEmpty()) {
                        ctx.resources.getQuantityString(
                            R.plurals.charge_cards_compatible_num,
                            loc.chargecards.size, loc.chargecards.size
                        )
                    } else null
                ).joinToString(", "),
                if (loc.chargecards != null && loc.chargecards.isNotEmpty()) {
                    formatChargeCards(loc.chargecards, chargeCards, filteredChargeCards, ctx)
                } else null,
                clickable = true
            ) else null,
        DetailsAdapter.Detail(
            R.drawable.ic_location,
            R.string.coordinates,
            loc.coordinates.formatDMS(),
            loc.coordinates.formatDecimal(),
            links = false,
            clickable = true
        ),
    )
}

fun formatTeslaParkingFee(teslaPricing: Pricing, ctx: Context) =
    teslaPricing.memberRates?.activePricebook?.parking?.let { parkingFee ->
        ctx.getString(
            R.string.tesla_pricing_blocking_fee,
            formatTeslaPricingRate(parkingFee.rates, parkingFee.currencyCode, parkingFee.uom, ctx)
        )
    }

fun formatTeslaPricing(teslaPricing: Pricing, ctx: Context) =
    buildSpannedString {
        teslaPricing.memberRates?.let { memberRates ->
            append(
                ctx.getString(if (teslaPricing.userRates != null) R.string.tesla_pricing_members else R.string.tesla_pricing_owners),
                StyleSpan(Typeface.BOLD),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(formatTeslaPricingRates(memberRates, ctx))
        }
        teslaPricing.userRates?.let { userRates ->
            append("\n\n")
            append(
                ctx.getString(R.string.tesla_pricing_others),
                StyleSpan(Typeface.BOLD),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(formatTeslaPricingRates(userRates, ctx))
        }
    }

private fun formatTeslaPricingRates(rates: Rates, ctx: Context) =
    buildSpannedString {
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        if (rates.activePricebook.charging.touRates.enabled) {
            // time-of-day-based rates
            val ratesByTime = rates.activePricebook.charging.touRates.activeRatesByTime
            val distinctRates =
                ratesByTime.map { it.rates }.distinct().sortedByDescending { it.max() }
            if (distinctRates.size == 2) {
                // special case: only list periods with higher price
                val highPriceTimes = ratesByTime.filter { it.rates == distinctRates[0] }
                append("\n")
                append(highPriceTimes.joinToString(", ") {
                    timeFmt.format(it.startTime) + " - " + timeFmt.format(it.endTime)
                } + ": ", StyleSpan(Typeface.ITALIC), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(
                    formatTeslaPricingRate(
                        distinctRates[0],
                        rates.activePricebook.charging.currencyCode,
                        rates.activePricebook.charging.uom,
                        ctx
                    )
                )
                append("\n")
                append(
                    ctx.getString(R.string.tesla_pricing_other_times),
                    StyleSpan(Typeface.ITALIC),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append(" ")
                append(
                    formatTeslaPricingRate(
                        distinctRates[1],
                        rates.activePricebook.charging.currencyCode,
                        rates.activePricebook.charging.uom,
                        ctx
                    )
                )
            } else {
                // general case
                ratesByTime.forEach { rate ->
                    append("\n")
                    append(
                        timeFmt.format(rate.startTime) + " - " + timeFmt.format(rate.endTime) + ": ",
                        StyleSpan(Typeface.ITALIC),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append(
                        formatTeslaPricingRate(
                            rate.rates,
                            rates.activePricebook.charging.currencyCode,
                            rates.activePricebook.charging.uom,
                            ctx
                        )
                    )
                }
            }
        } else {
            // fixed rates
            append(" ")
            append(
                formatTeslaPricingRate(
                    rates.activePricebook.charging.rates,
                    rates.activePricebook.charging.currencyCode,
                    rates.activePricebook.charging.uom,
                    ctx
                )
            )
        }
    }

private fun formatTeslaPricingRate(
    rates: List<Double>,
    currencyCode: String,
    uom: String,
    ctx: Context
): String {
    if (rates.isEmpty()) return ""
    val rate = rates.max()
    val value = ctx.getString(
        when (uom) {
            "kwh" -> R.string.charge_price_kwh_format
            "min" -> R.string.charge_price_minute_format
            else -> return ""
        }, rate, currency(currencyCode)
    )
    return if (rates.size > 1) {
        ctx.getString(R.string.pricing_up_to, value)
    } else {
        value
    }
}

fun formatChargeCards(
    chargecards: List<ChargeCardId>,
    chargecardData: Map<Long, ChargeCard>?,
    filteredChargeCards: Set<Long>?,
    ctx: Context
): CharSequence {
    if (chargecardData == null) return ""

    val maxItems = 5
    var result = chargecards
        .sortedByDescending { filteredChargeCards?.contains(it.id) }
        .take(maxItems)
        .mapNotNull {
            val name = chargecardData[it.id]?.name ?: return@mapNotNull null
            if (filteredChargeCards?.contains(it.id) == true) {
                name.bold()
            } else {
                name
            }
        }.joinToSpannedString()
    if (chargecards.size > maxItems) {
        result += " " + ctx.getString(R.string.and_n_others, chargecards.size - maxItems)
    }

    return result
}