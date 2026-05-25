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

        binding.txtName.text = item.charger.name
        binding.txtAddress.text = item.charger.address?.toString()
        binding.txtAddress.visibility = invisibleUnless(item.charger.address != null)

        binding.txtConnectors.text =
            item.charger.formatChargepoints(context.stringProvider(), locale)

        binding.txtDistance.text = distance(item.distance, context)
        binding.txtDistance.visibility = goneUnless(item.distance != null)

        val availableData = item.available.data
        if (availableData != null) {
            binding.txtStatus.text = String.format(
                locale,
                "%s/%d",
                availabilityText(availableData),
                item.total
            )
            binding.txtStatus.backgroundTintList =
                ColorStateList.valueOf(availabilityColor(availableData, context))
        }
        binding.txtStatus.visibility = invisibleUnless(item.available.status == Status.SUCCESS)
        binding.progressBar.visibility = goneUnless(item.available.status == Status.LOADING)

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