package net.vonforst.evmap.adapter

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.text.buildSpannedString
import androidx.viewbinding.ViewBinding
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.tesla.Pricing
import net.vonforst.evmap.api.availability.tesla.Rates
import net.vonforst.evmap.databinding.ItemDetailBinding
import net.vonforst.evmap.databinding.ItemDetailOpeninghoursBinding
import net.vonforst.evmap.joinToSpannedString
import net.vonforst.evmap.model.ChargeCard
import net.vonforst.evmap.model.ChargeCardId
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.OpeningHoursDays
import net.vonforst.evmap.plus
import net.vonforst.evmap.ui.applySelectableItemBackground
import net.vonforst.evmap.ui.goneUnless
import net.vonforst.evmap.ui.setLinkify
import net.vonforst.evmap.ui.currency
import net.vonforst.evmap.utils.formatDMS
import net.vonforst.evmap.utils.formatDecimal
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

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
        return if (item.hoursDays != null) {
            R.layout.item_detail_openinghours
        } else {
            R.layout.item_detail
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding = when (viewType) {
        R.layout.item_detail_openinghours -> ItemDetailOpeninghoursBinding.inflate(
            inflater,
            parent,
            false
        )

        else -> ItemDetailBinding.inflate(inflater, parent, false)
    }

    override fun bind(holder: ViewHolder, item: Detail) {
        super.bind(holder, item)
        if (holder.binding is ItemDetailBinding) {
            bindNormal(holder.binding, item)
        } else {
            bindOpeningHours(holder.binding as ItemDetailOpeninghoursBinding, item)
        }
    }

    private fun bindNormal(binding: ItemDetailBinding, item: Detail) {
        binding.root.isClickable = item.clickable
        applySelectableItemBackground(binding.root, item.clickable)

        binding.imageView3.setImageResource(item.icon)
        binding.imageView3.contentDescription =
            binding.root.context.getString(item.contentDescription)
        binding.txtTariff.text = item.text

        binding.txtProvider.text = item.detailText
        goneUnless(binding.txtProvider, item.detailText != null)
        setLinkify(
            binding.txtProvider,
            if (item.links) Linkify.WEB_URLS or Linkify.PHONE_NUMBERS else 0,
            item.detailText
        )
    }

    private fun bindOpeningHours(binding: ItemDetailOpeninghoursBinding, item: Detail) {
        binding.root.isClickable = item.clickable
        applySelectableItemBackground(binding.root, item.clickable)

        binding.imageView3.setImageResource(item.icon)
        binding.imageView3.contentDescription =
            binding.root.context.getString(item.contentDescription)
        binding.txtTariff.text = item.text

        binding.txtProvider.text = item.detailText
        goneUnless(binding.txtProvider, item.detailText != null)
        setLinkify(
            binding.txtProvider,
            if (item.links) Linkify.WEB_URLS or Linkify.PHONE_NUMBERS else 0,
            item.detailText
        )

        val toggleMarginTop = binding.root.resources.getDimensionPixelSize(
            if (item.detailText != null) {
                R.dimen.expand_toggle_padding_large
            } else {
                R.dimen.expand_toggle_padding_small
            }
        )
        val toggleLayoutParams = binding.expandToggle.layoutParams
        if (toggleLayoutParams is ViewGroup.MarginLayoutParams) {
            toggleLayoutParams.topMargin = toggleMarginTop
            binding.expandToggle.layoutParams = toggleLayoutParams
        }

        val days = item.hoursDays
        val expanded = days != null
        binding.expandToggle.visibility =
            if (expanded) android.view.View.VISIBLE else android.view.View.GONE
        if (!expanded) return

        bindDay(binding.hoursMon, DayOfWeek.MONDAY, days)
        bindDay(binding.hoursTue, DayOfWeek.TUESDAY, days)
        bindDay(binding.hoursWed, DayOfWeek.WEDNESDAY, days)
        bindDay(binding.hoursThu, DayOfWeek.THURSDAY, days)
        bindDay(binding.hoursFri, DayOfWeek.FRIDAY, days)
        bindDay(binding.hoursSat, DayOfWeek.SATURDAY, days)
        bindDay(binding.hoursSun, DayOfWeek.SUNDAY, days)
        bindDay(binding.hoursHoliday, null, days)

        val toggleListener = android.view.View.OnClickListener {
            TransitionManager.beginDelayedTransition(binding.container)
            val show = binding.expandToggle.isChecked
            val visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            listOf(
                binding.hoursMon.root,
                binding.hoursTue.root,
                binding.hoursWed.root,
                binding.hoursThu.root,
                binding.hoursFri.root,
                binding.hoursSat.root,
                binding.hoursSun.root,
                binding.hoursHoliday.root
            ).forEach { it.visibility = visibility }
        }
        binding.expandToggle.setOnClickListener(toggleListener)
        binding.expandToggle.isChecked = false
        toggleListener.onClick(binding.expandToggle)
    }

    private fun bindDay(
        include: net.vonforst.evmap.databinding.ItemDetailOpeninghoursItemBinding,
        dayOfWeek: DayOfWeek?,
        hours: OpeningHoursDays
    ) {
        include.textView24.text = if (dayOfWeek != null) {
            dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        } else {
            include.root.context.getString(R.string.holiday)
        }
        include.textView25.text = hours.getHoursForDayOfWeek(dayOfWeek)?.toString()
            ?: include.root.context.getString(R.string.closed_unfmt)
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
                buildSpannedString {
                    append(name, StyleSpan(Typeface.BOLD), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                name
            }
        }.joinToSpannedString()
    if (chargecards.size > maxItems) {
        result += " " + ctx.getString(R.string.and_n_others, chargecards.size - maxItems)
    }

    return result
}
