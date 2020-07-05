package net.vonforst.evmap.adapter

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.databinding.Observable
import com.google.android.material.chip.Chip
import net.vonforst.evmap.BR
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.ItemFilterMultipleChoiceBinding
import net.vonforst.evmap.databinding.ItemFilterMultipleChoiceLargeBinding
import net.vonforst.evmap.databinding.ItemFilterSliderBinding
import net.vonforst.evmap.fragment.MultiSelectDialog
import net.vonforst.evmap.viewmodel.*
import kotlin.math.max

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
            val dialog =
                MultiSelectDialog.getInstance(
                    filter.name,
                    filter.choices,
                    value.values
                )
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
        binding.progress =
            max(filter.inverseMapping(value.value) - filter.min, 0)
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