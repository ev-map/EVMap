package net.vonforst.evmap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.viewbinding.ViewBinding
import com.google.android.material.chip.Chip
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.ItemFilterBooleanBinding
import net.vonforst.evmap.databinding.ItemFilterMultipleChoiceBinding
import net.vonforst.evmap.databinding.ItemFilterMultipleChoiceLargeBinding
import net.vonforst.evmap.databinding.ItemFilterSliderBinding
import net.vonforst.evmap.fragment.MultiSelectDialog
import net.vonforst.evmap.model.BooleanFilter
import net.vonforst.evmap.model.BooleanFilterValue
import net.vonforst.evmap.model.FilterValue
import net.vonforst.evmap.model.FilterWithValue
import net.vonforst.evmap.model.MultipleChoiceFilter
import net.vonforst.evmap.model.MultipleChoiceFilterValue
import net.vonforst.evmap.model.SliderFilter
import net.vonforst.evmap.model.SliderFilterValue
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

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding = when (viewType) {
        R.layout.item_filter_boolean -> ItemFilterBooleanBinding.inflate(inflater, parent, false)
        R.layout.item_filter_multiple_choice -> ItemFilterMultipleChoiceBinding.inflate(
            inflater,
            parent,
            false
        )

        R.layout.item_filter_multiple_choice_large -> ItemFilterMultipleChoiceLargeBinding.inflate(
            inflater,
            parent,
            false
        )

        R.layout.item_filter_slider -> ItemFilterSliderBinding.inflate(inflater, parent, false)
        else -> error("Unknown viewType: $viewType")
    }

    override fun bind(
        holder: ViewHolder,
        item: FilterWithValue<FilterValue>
    ) {
        super.bind(holder, item)
        when (item.value) {
            is SliderFilterValue -> {
                setupSlider(
                    holder.binding as ItemFilterSliderBinding,
                    item.filter as SliderFilter,
                    item.value
                )
            }
            is MultipleChoiceFilterValue -> {
                val filter = item.filter as MultipleChoiceFilter
                if (filter.manyChoices) {
                    setupMultipleChoiceMany(
                        holder.binding as ItemFilterMultipleChoiceLargeBinding,
                        filter,
                        item.value
                    )
                } else {
                    setupMultipleChoice(
                        holder.binding as ItemFilterMultipleChoiceBinding,
                        filter,
                        item.value
                    )
                }
            }
            is BooleanFilterValue -> {
                setupBoolean(holder.binding as ItemFilterBooleanBinding, item)
            }
        }
    }

    private fun setupBoolean(
        binding: ItemFilterBooleanBinding,
        item: FilterWithValue<FilterValue>
    ) {
        val filter = item.filter as BooleanFilter
        val value = item.value as BooleanFilterValue
        binding.textView17.text = filter.name
        binding.switch1.setOnCheckedChangeListener(null)
        binding.switch1.isChecked = value.value
        binding.switch1.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            value.value = isChecked
        }
    }

    private fun setupMultipleChoice(
        binding: ItemFilterMultipleChoiceBinding,
        filter: MultipleChoiceFilter,
        value: MultipleChoiceFilterValue
    ) {
        val inflater = LayoutInflater.from(binding.root.context)
        binding.textView17.text = filter.name

        value.values.toList().forEach {
            if (it !in filter.choices.keys) value.values.remove(it)
        }

        var showingAll = false
        fun updateButtons() {
            value.all = value.values == filter.choices.keys
            binding.btnAll.isEnabled = !value.all
            binding.btnNone.isEnabled = value.values.isNotEmpty()
            binding.chipMore.text = binding.root.context.getString(
                if (showingAll) R.string.show_less else R.string.show_more
            )
        }

        val chips = mutableMapOf<String, Chip>()

        val reuseChips = binding.chipGroup.children.filter {
            it.id != R.id.chipMore
        }.toMutableList()
        binding.chipGroup.children.toList().forEach {
            if (it.id != R.id.chipMore) binding.chipGroup.removeView(it)
        }

        filter.choices.entries.sortedByDescending {
            it.key in value.values
        }.sortedByDescending {
            if (filter.commonChoices != null) it.key in filter.commonChoices else false
        }.forEach { choice ->
            var reused = false
            val chip = if (reuseChips.isNotEmpty()) {
                reused = true
                reuseChips.removeAt(0) as Chip
            } else {
                inflater.inflate(
                    R.layout.item_filter_multiple_choice_chip,
                    binding.chipGroup,
                    false
                ) as Chip
            }
            chip.text = choice.value
            chip.setOnCheckedChangeListener(null)
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
                && !(chip.isChecked && !value.all) && !showingAll
            ) {
                chip.visibility = View.GONE
            } else {
                chip.visibility = View.VISIBLE
            }

            if (!reused) binding.chipGroup.addView(chip, binding.chipGroup.childCount - 1)
            chips[choice.key] = chip
        }

        reuseChips.forEach {
            binding.chipGroup.removeView(it)
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
            showingAll = !showingAll
            chips.forEach { (key, chip) ->
                if (filter.commonChoices != null && key !in filter.commonChoices
                    && !(chip.isChecked && !value.all) && !showingAll
                ) {
                    chip.visibility = View.GONE
                } else {
                    chip.visibility = View.VISIBLE
                }
            }
            updateButtons()
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
        }

        binding.textView17.text = filter.name
        binding.textView26.text = binding.root.context.getString(
            if (value.all) {
                R.string.all_selected
            } else {
                R.string.number_selected
            },
            value.values.size
        )

        binding.btnEdit.setOnClickListener {
            val dialog = MultiSelectDialog.getInstance(
                filter.name,
                filter.choices,
                value.values,
                commonChoices = filter.commonChoices
            )
            dialog.okListener = { selected ->
                value.values = selected.toMutableSet()
                value.all = value.values == filter.choices.keys
                binding.textView26.text = binding.root.context.getString(
                    if (value.all) {
                        R.string.all_selected
                    } else {
                        R.string.number_selected
                    },
                    value.values.size
                )
            }
            dialog.show((binding.root.context as AppCompatActivity).supportFragmentManager, null)
        }
    }

    private fun setupSlider(
        binding: ItemFilterSliderBinding,
        filter: SliderFilter,
        value: SliderFilterValue
    ) {
        binding.textView17.text = filter.name
        val progress = max(filter.inverseMapping(value.value) - filter.min, 0)
        binding.seekBar.max = filter.max - filter.min
        binding.seekBar.setOnSeekBarChangeListener(null)
        binding.seekBar.progress = progress
        updateSliderValueText(binding, filter, progress)

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val mapped = filter.mapping(progress + filter.min)
                value.value = mapped
                updateSliderValueText(binding, filter, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun updateSliderValueText(
        binding: ItemFilterSliderBinding,
        filter: SliderFilter,
        progress: Int
    ) {
        val mappedValue = filter.mapping(progress + filter.min)
        binding.textView18.text = if (filter.unit.isNullOrBlank()) {
            mappedValue.toString()
        } else {
            "$mappedValue ${filter.unit}"
        }
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