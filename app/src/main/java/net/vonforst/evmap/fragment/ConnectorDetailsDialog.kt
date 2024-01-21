package net.vonforst.evmap.fragment

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.ConnectorAdapter
import net.vonforst.evmap.adapter.ConnectorDetailsAdapter
import net.vonforst.evmap.adapter.SingleViewAdapter
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.databinding.DialogConnectorDetailsBinding
import net.vonforst.evmap.databinding.DialogConnectorDetailsHeaderBinding
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.storage.PreferenceDataSource

class ConnectorDetailsDialog(
    val binding: DialogConnectorDetailsBinding,
    context: Context,
    onClose: () -> Unit
) {
    private val headerBinding: DialogConnectorDetailsHeaderBinding
    private val detailsAdapter = ConnectorDetailsAdapter()

    init {
        binding.list.apply {
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }
        headerBinding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.dialog_connector_details_header, binding.list, false
        )
        binding.list.adapter = ConcatAdapter(
            SingleViewAdapter(headerBinding.root),
            detailsAdapter
        )
        binding.btnClose.setOnClickListener {
            onClose()
        }
    }

    fun setData(cp: Chargepoint, status: ChargeLocationStatus?) {
        val cpStatus = status?.status?.get(cp)
        val items = if (status != null) {
            List(cp.count) { i ->
                ConnectorDetailsAdapter.ConnectorDetails(
                    cpStatus?.get(i),
                    status.evseIds?.get(cp)?.get(i),
                    status.labels?.get(cp)?.get(i),
                    status.lastChange?.get(cp)?.get(i)
                )
            }.sortedBy { it.evseId ?: it.label }
        } else emptyList()
        detailsAdapter.submitList(items)

        headerBinding.divider.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        headerBinding.item = ConnectorAdapter.ChargepointWithAvailability(cp, cpStatus)
    }
}