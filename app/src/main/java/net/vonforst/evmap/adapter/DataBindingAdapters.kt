package net.vonforst.evmap.adapter

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
import net.vonforst.evmap.model.Chargepoint
import java.time.Instant

interface Equatable {
    override fun equals(other: Any?): Boolean
}

abstract class DataBindingAdapter<T : Equatable>(getKey: ((T) -> Any)? = null) :
    ListAdapter<T, DataBindingAdapter.ViewHolder<T>>(DiffCallback(getKey)) {

    var onClickListener: ((T) -> Unit)? = null
    var onLongClickListener: ((T) -> Boolean)? = null

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
        if (onClickListener != null) {
            holder.binding.root.setOnClickListener {
                val listener = onClickListener ?: return@setOnClickListener
                listener(item)
            }
        }
        if (onLongClickListener != null) {
            holder.binding.root.setOnLongClickListener {
                val listener = onLongClickListener ?: return@setOnLongClickListener false
                return@setOnLongClickListener listener(item)
            }
        }
    }

    class DiffCallback<T : Equatable>(val getKey: ((T) -> Any)?) : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = if (getKey != null) {
            (getKey)(oldItem) == (getKey)(newItem)
        } else {
            oldItem === newItem
        }

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

class ConnectorDetailsAdapter : DataBindingAdapter<ConnectorDetailsAdapter.ConnectorDetails>() {
    data class ConnectorDetails(
        val status: ChargepointStatus?,
        val evseId: String?,
        val label: String?,
        val lastChange: Instant?
    ) :
        Equatable

    override fun getItemViewType(position: Int): Int = R.layout.dialog_connector_details_item
}