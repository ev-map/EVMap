package net.vonforst.evmap.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import net.vonforst.evmap.BR
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.goingelectric.*
import net.vonforst.evmap.databinding.ItemFilterMultipleChoiceBinding
import net.vonforst.evmap.databinding.ItemFilterMultipleChoiceLargeBinding
import net.vonforst.evmap.databinding.ItemFilterSliderBinding
import net.vonforst.evmap.fragment.MultiSelectDialog
import net.vonforst.evmap.viewmodel.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max

interface Equatable {
    override fun equals(other: Any?): Boolean;
}

abstract class DataBindingAdapter<T : Equatable>(getKey: ((T) -> Any)? = null) :
    ListAdapter<T, DataBindingAdapter.ViewHolder<T>>(DiffCallback(getKey)) {

    var onClickListener: ((T) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding =
            DataBindingUtil.inflate<ViewDataBinding>(layoutInflater, viewType, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) =
        bind(holder, getItem(position))

    class ViewHolder<T>(val binding: ViewDataBinding) :
        RecyclerView.ViewHolder(binding.root) {
    }

    open fun bind(holder: ViewHolder<T>, item: T) {
        holder.binding.setVariable(BR.item, item)
        holder.binding.executePendingBindings()
        if (onClickListener != null) {
            holder.binding.root.setOnClickListener {
                val listener = onClickListener ?: return@setOnClickListener
                listener(item)
            }
        }
    }

    class DiffCallback<T : Equatable>(val getKey: ((T) -> Any)?) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = if (getKey != null) {
            (getKey)(oldItem) == (getKey)(newItem)
        } else {
            oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
    }
}

fun chargepointWithAvailability(
    chargepoints: Iterable<Chargepoint>?,
    availability: Map<Chargepoint, List<ChargepointStatus>>?
): List<ConnectorAdapter.ChargepointWithAvailability>? {
    return chargepoints?.map {
        val status = availability?.get(it)
        ConnectorAdapter.ChargepointWithAvailability(it, status)
    }
}

class ConnectorAdapter : DataBindingAdapter<ConnectorAdapter.ChargepointWithAvailability>() {
    data class ChargepointWithAvailability(
        val chargepoint: Chargepoint,
        val status: List<ChargepointStatus>?
    ) :
        Equatable

    override fun getItemViewType(position: Int): Int = R.layout.item_connector
}

class DetailAdapter : DataBindingAdapter<DetailAdapter.Detail>() {
    data class Detail(
        val icon: Int,
        val contentDescription: Int,
        val text: CharSequence,
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
    ctx: Context
): List<DetailAdapter.Detail> {
    if (loc == null) return emptyList()

    return listOfNotNull(
        DetailAdapter.Detail(
            R.drawable.ic_address,
            R.string.address,
            loc.address.toString(),
            loc.locationDescription
        ),
        if (loc.operator != null) DetailAdapter.Detail(
            R.drawable.ic_operator,
            R.string.operator,
            loc.operator
        ) else null,
        if (loc.network != null) DetailAdapter.Detail(
            R.drawable.ic_network,
            R.string.network,
            loc.network
        ) else null,
        if (loc.faultReport != null) DetailAdapter.Detail(
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
                HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY)
            } ?: "",
            clickable = true
        ) else null,
        if (loc.openinghours != null && !loc.openinghours.isEmpty) DetailAdapter.Detail(
            R.drawable.ic_hours,
            R.string.hours,
            loc.openinghours.getStatusText(ctx),
            loc.openinghours.description,
            hoursDays = loc.openinghours.days
        ) else null,
        if (loc.cost != null) DetailAdapter.Detail(
            R.drawable.ic_cost,
            R.string.cost,
            loc.cost.getStatusText(ctx),
            loc.cost.descriptionLong ?: loc.cost.descriptionShort
        )
        else null,
        if (loc.chargecards != null && loc.chargecards.isNotEmpty()) DetailAdapter.Detail(
            R.drawable.ic_payment,
            R.string.charge_cards,
            ctx.resources.getQuantityString(
                R.plurals.charge_cards_compatible_num,
                loc.chargecards.size, loc.chargecards.size
            ),
            formatChargeCards(loc.chargecards, chargeCards, ctx),
            clickable = true
        ) else null,
        DetailAdapter.Detail(
            R.drawable.ic_location,
            R.string.coordinates,
            loc.coordinates.formatDMS(),
            loc.coordinates.formatDecimal(),
            links = false,
            clickable = true
        )
    )
}

fun formatChargeCards(
    chargecards: List<ChargeCardId>,
    chargecardData: Map<Long, ChargeCard>?,
    ctx: Context
): String {
    if (chargecardData == null) return ""

    val maxItems = 5
    var result = chargecards
        .take(maxItems)
        .mapNotNull { chargecardData[it.id]?.name }
        .joinToString()
    if (chargecards.size > maxItems) {
        result += " " + ctx.getString(R.string.and_n_others, chargecards.size - maxItems)
    }

    return result
}


class FavoritesAdapter(val vm: FavoritesViewModel) :
    DataBindingAdapter<FavoritesViewModel.FavoritesListItem>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int = R.layout.item_favorite

    override fun getItemId(position: Int): Long = getItem(position).charger.id
}

class FiltersAdapter : DataBindingAdapter<FilterWithValue<FilterValue>>() {
    init {
        setHasStableIds(true)
    }

    val itemids = mutableMapOf<String, Long>()
    var maxId = 0L

    override fun getItemViewType(position: Int): Int =
        when (val filter = getItem(position).filter) {
            is BooleanFilter -> R.layout.item_filter_boolean
            is MultipleChoiceFilter -> {
                if (filter.manyChoices) {
                    R.layout.item_filter_multiple_choice_large
                } else {
                    R.layout.item_filter_multiple_choice
                }
            }
            is SliderFilter -> R.layout.item_filter_slider
        }

    override fun bind(
        holder: ViewHolder<FilterWithValue<FilterValue>>,
        item: FilterWithValue<FilterValue>
    ) {
        super.bind(holder, item)
        when (item.value) {
            is SliderFilterValue -> {
                setupSlider(
                    holder.binding as ItemFilterSliderBinding,
                    item.filter as SliderFilter, item.value
                )
            }
            is MultipleChoiceFilterValue -> {
                val filter = item.filter as MultipleChoiceFilter
                if (filter.manyChoices) {
                    setupMultipleChoiceMany(
                        holder.binding as ItemFilterMultipleChoiceLargeBinding,
                        filter, item.value
                    )
                } else {
                    setupMultipleChoice(
                        holder.binding as ItemFilterMultipleChoiceBinding,
                        filter, item.value
                    )
                }
            }
        }
    }

    private fun setupMultipleChoice(
        binding: ItemFilterMultipleChoiceBinding,
        filter: MultipleChoiceFilter,
        value: MultipleChoiceFilterValue
    ) {
        val inflater = LayoutInflater.from(binding.root.context)
        value.values.toList().forEach {
            // delete values that cannot be selected anymore
            if (it !in filter.choices.keys) value.values.remove(it)
        }

        fun updateButtons() {
            value.all = value.values == filter.choices.keys
            binding.btnAll.isEnabled = !value.all
            binding.btnNone.isEnabled = value.values.isNotEmpty()
        }

        val chips = mutableMapOf<String, Chip>()
        binding.chipGroup.children.forEach {
            if (it.id != R.id.chipMore) binding.chipGroup.removeView(it)
        }
        filter.choices.entries.sortedByDescending {
            it.key in value.values
        }.sortedByDescending {
            if (filter.commonChoices != null) it.key in filter.commonChoices else false
        }.forEach { choice ->
            val chip = inflater.inflate(
                R.layout.item_filter_multiple_choice_chip,
                binding.chipGroup,
                false
            ) as Chip
            chip.text = choice.value
            chip.isChecked = choice.key in value.values || value.all
            if (value.all && choice.key !in value.values) value.values.add(choice.key)

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    value.values.add(choice.key)
                } else {
                    value.values.remove(choice.key)
                }
                updateButtons()
            }

            if (filter.commonChoices != null && choice.key !in filter.commonChoices
                && !(chip.isChecked && !value.all) && !binding.showingAll
            ) {
                chip.visibility = View.GONE
            } else {
                chip.visibility = View.VISIBLE
            }

            binding.chipGroup.addView(chip, binding.chipGroup.childCount - 1)
            chips[choice.key] = chip
        }

        binding.btnAll.setOnClickListener {
            value.all = true
            value.values.addAll(filter.choices.keys)
            chips.values.forEach { it.isChecked = true }
            updateButtons()
        }
        binding.btnNone.setOnClickListener {
            value.all = true
            value.values.addAll(filter.choices.keys)
            chips.values.forEach { it.isChecked = false }
            updateButtons()
        }
        binding.chipMore.setOnClickListener {
            binding.showingAll = !binding.showingAll
            chips.forEach { (key, chip) ->
                if (filter.commonChoices != null && key !in filter.commonChoices
                    && !(chip.isChecked && !value.all) && !binding.showingAll
                ) {
                    chip.visibility = View.GONE
                } else {
                    chip.visibility = View.VISIBLE
                }
            }
        }
        updateButtons()
    }

    private fun setupMultipleChoiceMany(
        binding: ItemFilterMultipleChoiceLargeBinding,
        filter: MultipleChoiceFilter,
        value: MultipleChoiceFilterValue
    ) {
        if (value.all) {
            value.values = filter.choices.keys.toMutableSet()
            binding.notifyPropertyChanged(BR.item)
        }

        binding.btnEdit.setOnClickListener {
            val dialog = MultiSelectDialog.getInstance(filter.name, filter.choices, value.values)
            dialog.okListener = { selected ->
                value.values = selected.toMutableSet()
                value.all = value.values == filter.choices.keys
                binding.item = binding.item
            }
            dialog.show((binding.root.context as AppCompatActivity).supportFragmentManager, null)
        }
    }

    private fun setupSlider(
        binding: ItemFilterSliderBinding,
        filter: SliderFilter,
        value: SliderFilterValue
    ) {
        binding.progress = max(filter.inverseMapping(value.value) - filter.min, 0)
        binding.mappedValue = filter.mapping(binding.progress + filter.min)

        binding.addOnPropertyChangedCallback(object :
            Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                when (propertyId) {
                    BR.progress -> {
                        val mapped = filter.mapping(binding.progress + filter.min)
                        value.value = mapped
                        binding.mappedValue = mapped
                    }
                }
            }
        })
    }

    override fun getItemId(position: Int): Long {
        val key = getItem(position).filter.key
        var value = itemids[key]
        if (value == null) {
            maxId++
            value = maxId
            itemids[key] = maxId
        }
        return value
    }
}

class DonationAdapter() : DataBindingAdapter<DonationItem>() {
    override fun getItemViewType(position: Int): Int = R.layout.item_donation
}