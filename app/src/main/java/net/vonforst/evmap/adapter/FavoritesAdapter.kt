package net.vonforst.evmap.adapter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import androidx.viewbinding.ViewBinding
import net.vonforst.evmap.R
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.databinding.ItemFavoriteBinding
import net.vonforst.evmap.ui.availabilityColor
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.distance
import net.vonforst.evmap.ui.goneUnless
import net.vonforst.evmap.ui.invisibleUnless
import net.vonforst.evmap.viewmodel.FavoritesViewModel
import net.vonforst.evmap.viewmodel.Status
import java.util.Locale

class FavoritesAdapter(val onDelete: (FavoritesViewModel.FavoritesListItem) -> Unit) :
    DataBindingAdapter<FavoritesViewModel.FavoritesListItem>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int = R.layout.item_favorite

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding = ItemFavoriteBinding.inflate(inflater, parent, false)

    override fun getItemId(position: Int): Long = getItem(position).fav.favorite.favoriteId

    @SuppressLint("ClickableViewAccessibility")
    override fun bind(
        holder: ViewHolder,
        item: FavoritesViewModel.FavoritesListItem
    ) {
        super.bind(holder, item)

        val binding = holder.binding as ItemFavoriteBinding
        val context = binding.root.context
        val locale = Locale.getDefault()

        binding.textView15.text = item.charger.name
        binding.textView2.text = item.charger.address?.toString()
        invisibleUnless(binding.textView2, item.charger.address != null)

        binding.txtConnectors.text =
            item.charger.formatChargepoints(context.stringProvider(), locale)

        binding.textView16.text = distance(item.distance, context)
        goneUnless(binding.textView16, item.distance != null)

        val availableData = item.available.data
        if (availableData != null) {
            binding.textView7.text = String.format(
                locale,
                "%s/%d",
                availabilityText(availableData),
                item.total
            )
            binding.textView7.backgroundTintList =
                ColorStateList.valueOf(availabilityColor(availableData, context))
        }
        invisibleUnless(binding.textView7, item.available.status == Status.SUCCESS)
        goneUnless(binding.progressBar4, item.available.status == Status.LOADING)

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
    }
}