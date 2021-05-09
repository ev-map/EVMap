package net.vonforst.evmap.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.vonforst.evmap.BR
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.api.chargeprice.ChargePrice
import net.vonforst.evmap.api.chargeprice.ChargepriceChargepointMeta
import net.vonforst.evmap.api.chargeprice.ChargepriceTag
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.databinding.ItemChargepriceBinding
import net.vonforst.evmap.databinding.ItemConnectorButtonBinding
import net.vonforst.evmap.ui.CheckableConstraintLayout
import net.vonforst.evmap.viewmodel.FavoritesViewModel

interface Equatable {
    override fun equals(other: Any?): Boolean;
}

abstract class DataBindingAdapter<T : Equatable>(getKey: ((T) -> Any)? = null) :
    ListAdapter<T, DataBindingAdapter.ViewHolder<T>>(DiffCallback(getKey)) {

    var onClickListener: ((T) -> Unit)? = null

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


class FavoritesAdapter(val vm: FavoritesViewModel) :
    DataBindingAdapter<FavoritesViewModel.FavoritesListItem>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int = R.layout.item_favorite

    override fun getItemId(position: Int): Long = getItem(position).charger.id
}

class ChargepriceAdapter() :
    DataBindingAdapter<ChargePrice>() {

    val viewPool = RecyclerView.RecycledViewPool();
    var meta: ChargepriceChargepointMeta? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var myTariffs: Set<String>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var myTariffsAll: Boolean? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = R.layout.item_chargeprice

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<ChargePrice> {
        val holder = super.onCreateViewHolder(parent, viewType)
        val binding = holder.binding as ItemChargepriceBinding
        binding.rvTags.apply {
            adapter = ChargepriceTagsAdapter()
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false).apply {
                    recycleChildrenOnDetach = true
                }
            itemAnimator = null
            setRecycledViewPool(viewPool)
        }
        return holder
    }

    override fun bind(holder: ViewHolder<ChargePrice>, item: ChargePrice) {
        super.bind(holder, item)
        (holder.binding as ItemChargepriceBinding).apply {
            this.meta = this@ChargepriceAdapter.meta
            this.myTariffs = this@ChargepriceAdapter.myTariffs
            this.myTariffsAll = this@ChargepriceAdapter.myTariffsAll
        }
    }
}

class CheckableConnectorAdapter : DataBindingAdapter<Chargepoint>() {
    private var checkedItem: Int? = 0

    var enabledConnectors: List<String>? = null
        get() = field
        set(value) {
            field = value
            checkedItem?.let {
                if (value != null && getItem(it).type !in value) {
                    val index = currentList.indexOfFirst {
                        it.type in value
                    }
                    checkedItem = if (index == -1) null else index
                    onCheckedItemChangedListener?.invoke(getCheckedItem())
                }
            }
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = R.layout.item_connector_button

    override fun onBindViewHolder(holder: ViewHolder<Chargepoint>, position: Int) {
        val item = getItem(position)
        super.bind(holder, item)
        val binding = holder.binding as ItemConnectorButtonBinding
        binding.enabled = enabledConnectors?.let { item.type in it } ?: true
        val root = binding.root as CheckableConstraintLayout
        root.isChecked = checkedItem == position
        root.setOnClickListener {
            root.isChecked = true
        }
        root.setOnCheckedChangeListener { v: View, checked: Boolean ->
            if (checked) {
                checkedItem = position
                notifyDataSetChanged()
                onCheckedItemChangedListener?.invoke(getCheckedItem()!!)
            }
        }
    }

    fun getCheckedItem(): Chargepoint? = checkedItem?.let { getItem(it) }

    fun setCheckedItem(item: Chargepoint?) {
        checkedItem = item?.let { currentList.indexOf(item) } ?: null
    }

    var onCheckedItemChangedListener: ((Chargepoint?) -> Unit)? = null
}

class ChargepriceTagsAdapter() :
    DataBindingAdapter<ChargepriceTag>() {
    override fun getItemViewType(position: Int): Int = R.layout.item_chargeprice_tag
}