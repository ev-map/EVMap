package net.vonforst.evmap.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.iconForPlugType
import net.vonforst.evmap.databinding.DialogConnectorDetailsItemBinding
import net.vonforst.evmap.databinding.ItemConnectorBinding
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.ui.availabilityColor
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.goneUnless
import java.time.Instant
import java.util.Locale

interface Equatable {
    override fun equals(other: Any?): Boolean
}

abstract class DataBindingAdapter<T : Equatable>(getKey: ((T) -> Any)? = null) :
    ListAdapter<T, DataBindingAdapter.ViewHolder>(DiffCallback(getKey)) {

    var onClickListener: ((T) -> Unit)? = null
    var onLongClickListener: ((T) -> Boolean)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return ViewHolder(createBinding(layoutInflater, parent, viewType))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        bind(holder, getItem(position))

    protected abstract fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding

    class ViewHolder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)

    open fun bind(holder: ViewHolder, item: T) {
        if (onClickListener != null) {
            holder.binding.root.setOnClickListener {
                val listener = onClickListener ?: return@setOnClickListener
                listener(item)
            }
        } else {
            holder.binding.root.setOnClickListener(null)
        }
        if (onLongClickListener != null) {
            holder.binding.root.setOnLongClickListener {
                val listener = onLongClickListener ?: return@setOnLongClickListener false
                return@setOnLongClickListener listener(item)
            }
        } else {
            holder.binding.root.setOnLongClickListener(null)
        }
    }

    class DiffCallback<T : Equatable>(private val getKey: ((T) -> Any)?) :
        DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = if (getKey != null) {
            getKey(oldItem) == getKey(newItem)
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
    ) : Equatable

    override fun getItemViewType(position: Int): Int = R.layout.item_connector

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding = ItemConnectorBinding.inflate(inflater, parent, false)

    override fun bind(holder: ViewHolder, item: ChargepointWithAvailability) {
        super.bind(holder, item)
        val binding = holder.binding as ItemConnectorBinding
        val context = binding.root.context
        val locale = Locale.getDefault()

        binding.imageView.setImageResource(iconForPlugType(item.chargepoint.type))
        binding.imageView.contentDescription = item.chargepoint.type
        binding.imageView.imageTintList =
            ColorStateList.valueOf(availabilityColor(item.status, context))

        binding.textView5.text = String.format(locale, "x %d", item.chargepoint.count)
        goneUnless(binding.textView5, item.status == null)

        if (item.status != null) {
            binding.textView7.text = String.format(
                locale,
                "%s/%d",
                availabilityText(item.status),
                item.chargepoint.count
            )
            binding.textView7.backgroundTintList =
                ColorStateList.valueOf(availabilityColor(item.status, context))
        }
        goneUnless(binding.textView7, item.status != null)

        binding.textView6.text = item.chargepoint.formatPower(locale)
        goneUnless(binding.textView6, item.chargepoint.hasKnownPower())
        binding.textView6.setTextColor(availabilityColor(item.status, context))
    }
}

class ConnectorDetailsAdapter : DataBindingAdapter<ConnectorDetailsAdapter.ConnectorDetails>() {
    data class ConnectorDetails(
        val status: ChargepointStatus?,
        val evseId: String?,
        val label: String?,
        val lastChange: Instant?
    ) : Equatable

    override fun getItemViewType(position: Int): Int = R.layout.dialog_connector_details_item

    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ViewBinding = DialogConnectorDetailsItemBinding.inflate(inflater, parent, false)

    override fun bind(holder: ViewHolder, item: ConnectorDetails) {
        super.bind(holder, item)
        val binding = holder.binding as DialogConnectorDetailsItemBinding
        val context = binding.root.context

        binding.imageView.imageTintList =
            ColorStateList.valueOf(availabilityColor(item.status, context))
        binding.imageView.contentDescription = availabilityText(item.status, null, context)

        binding.txtEvseid.text = when {
            item.label != null && item.evseId != null -> "${item.label} · ${item.evseId}"
            item.label != null -> item.label
            else -> item.evseId ?: ""
        }

        binding.txtStatus.text = availabilityText(item.status, item.lastChange, context)
        goneUnless(binding.txtStatus, item.status != null)
    }
}