package net.vonforst.evmap.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.ItemDonationBinding
import net.vonforst.evmap.viewmodel.DonationItem

class DonationAdapter : DataBindingAdapter<DonationItem>() {
    override fun getItemViewType(position: Int): Int = R.layout.item_donation

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding = ItemDonationBinding.inflate(inflater, parent, false)

    override fun bind(holder: ViewHolder, item: DonationItem) {
        super.bind(holder, item)
        val binding = holder.binding as ItemDonationBinding
        binding.textView15.text = item.product.title
        binding.textView21.text = item.product.oneTimePurchaseOfferDetails?.formattedPrice
    }
}