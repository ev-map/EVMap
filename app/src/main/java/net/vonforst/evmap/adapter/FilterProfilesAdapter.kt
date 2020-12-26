package net.vonforst.evmap.adapter

import android.view.MotionEvent
import androidx.recyclerview.widget.ItemTouchHelper
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.ItemFilterProfileBinding
import net.vonforst.evmap.storage.FilterProfile

class FilterProfilesAdapter(val dragHelper: ItemTouchHelper) : DataBindingAdapter<FilterProfile>() {
    init {
        setHasStableIds(true)
    }

    override fun bind(
        holder: ViewHolder<FilterProfile>,
        item: FilterProfile
    ) {
        super.bind(holder, item)

        val binding = holder.binding as ItemFilterProfileBinding
        binding.handle.setOnTouchListener { v, event ->
            if (event?.action == MotionEvent.ACTION_DOWN) {
                dragHelper.startDrag(holder)
            }
            false
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    override fun getItemViewType(position: Int): Int = R.layout.item_filter_profile
}