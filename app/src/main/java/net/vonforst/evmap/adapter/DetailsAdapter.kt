package net.vonforst.evmap.adapter

import android.content.Context
import androidx.core.text.HtmlCompat
import net.vonforst.evmap.R
import net.vonforst.evmap.bold
import net.vonforst.evmap.joinToSpannedString
import net.vonforst.evmap.model.ChargeCard
import net.vonforst.evmap.model.ChargeCardId
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.OpeningHoursDays
import net.vonforst.evmap.plus
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
    ctx: Context
): List<DetailsAdapter.Detail> {
    if (loc == null) return emptyList()

    return listOfNotNull(
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