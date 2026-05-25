package net.vonforst.evmap.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.viewbinding.ViewBinding
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.ItemFilterProfileBinding
import net.vonforst.evmap.storage.FilterProfile

class FilterProfilesAdapter(
    val dragHelper: ItemTouchHelper,
    val onDelete: (FilterProfile) -> Unit,
    val onRename: (FilterProfile) -> Unit
) : DataBindingAdapter<FilterProfile>() {
    init {
        setHasStableIds(true)
    }

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding = ItemFilterProfileBinding.inflate(inflater, parent, false)

    @SuppressLint("ClickableViewAccessibility")
    override fun bind(
        holder: ViewHolder,
        item: FilterProfile
    ) {
        super.bind(holder, item)

        val binding = holder.binding as ItemFilterProfileBinding
        binding.textView9.text = item.name
        binding.handle.setOnTouchListener { _, event ->
            if (event?.action == MotionEvent.ACTION_DOWN) {
                dragHelper.startDrag(holder)
            }
            false
        }
        binding.foreground.translationX = 0f
        binding.btnDelete.setOnClickListener {
            binding.foreground.animate()
                .translationX(binding.foreground.width.toFloat())
                .setDuration(250)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    onDelete(item)
                }
                .start()
        }
        binding.btnRename.setOnClickListener {
            onRename(item)
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    override fun getItemViewType(position: Int): Int = R.layout.item_filter_profile
}