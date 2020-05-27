package net.vonforst.evmap.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.databinding.ItemFilterMultipleChoiceBinding
import net.vonforst.evmap.databinding.ItemFilterSliderBinding
import net.vonforst.evmap.viewmodel.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

interface Equatable {
    override fun equals(other: Any?): Boolean;
}

abstract class DataBindingAdapter<T : Equatable>() :
    ListAdapter<T, DataBindingAdapter.ViewHolder<T>>(DiffCallback()) {

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
        holder.binding.root.setOnClickListener {
            val listener = onClickListener ?: return@setOnClickListener
            listener(item)
        }
    }

    class DiffCallback<T : Equatable> : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = oldItem === newItem

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
        val clickable: Boolean = false
    ) : Equatable

    override fun getItemViewType(position: Int): Int = R.layout.item_detail
}

fun buildDetails(loc: ChargeLocation?, ctx: Context): List<DetailAdapter.Detail> {
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
                ctx.getString(R.string.fault_report_date,
                    loc.faultReport.created
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)))
            } ?: "",
            loc.faultReport.description ?: "",
            clickable = true
        ) else null,
        // TODO: separate layout for opening hours with expandable details
        if (loc.openinghours != null && !loc.openinghours.isEmpty) DetailAdapter.Detail(
            R.drawable.ic_hours,
            R.string.hours,
            loc.openinghours.getStatusText(ctx),
            loc.openinghours.description
        ) else null,
        if (loc.cost != null) DetailAdapter.Detail(
            R.drawable.ic_cost,
            R.string.cost,
            loc.cost.getStatusText(ctx),
            loc.cost.descriptionLong ?: loc.cost.descriptionShort
        )
        else null,
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

    override fun getItemViewType(position: Int): Int = when (getItem(position).filter) {
        is BooleanFilter -> R.layout.item_filter_boolean
        is MultipleChoiceFilter -> R.layout.item_filter_multiple_choice
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
                setupMultipleChoice(
                    holder.binding as ItemFilterMultipleChoiceBinding,
                    item.filter as MultipleChoiceFilter, item.value
                )
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

    private fun setupSlider(
        binding: ItemFilterSliderBinding,
        filter: SliderFilter,
        value: SliderFilterValue
    ) {
        binding.progress = filter.inverseMapping(value.value)
        binding.mappedValue = value.value

        binding.addOnPropertyChangedCallback(object :
            Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                when (propertyId) {
                    BR.progress -> {
                        val mapped = filter.mapping(binding.progress)
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