package net.vonforst.evmap.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.vonforst.evmap.BR
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.viewmodel.FavoritesViewModel

interface Equatable {
    override fun equals(other: Any?): Boolean;
}

abstract class DataBindingAdapter<T : Equatable>() :
    ListAdapter<T, DataBindingAdapter.ViewHolder<T>>(DiffCallback()) {

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
        ConnectorAdapter.ChargepointWithAvailability(
            it, availability?.get(it)?.count { it == ChargepointStatus.AVAILABLE }
        )
    }
}

class ConnectorAdapter : DataBindingAdapter<ConnectorAdapter.ChargepointWithAvailability>() {
    data class ChargepointWithAvailability(val chargepoint: Chargepoint, val available: Int?) :
        Equatable

    override fun getItemViewType(position: Int): Int = R.layout.item_connector
}

class DetailAdapter : DataBindingAdapter<DetailAdapter.Detail>() {
    data class Detail(
        val icon: Int,
        val contentDescription: Int,
        val text: CharSequence,
        val detailText: CharSequence? = null,
        val links: Boolean = true
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
        // TODO: separate layout for opening hours with expandable details
        if (loc.openinghours != null) DetailAdapter.Detail(
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
            false
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