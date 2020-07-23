package net.vonforst.evmap.adapter

import net.vonforst.evmap.viewmodel.DonationItem

class DonationAdapter() : DataBindingAdapter<DonationItem>() {
    override fun getItemViewType(position: Int): Int = R.layout.item_donation
}