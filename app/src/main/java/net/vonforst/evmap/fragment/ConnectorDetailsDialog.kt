package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.ConnectorAdapter
import net.vonforst.evmap.adapter.ConnectorDetailsAdapter
import net.vonforst.evmap.adapter.SingleViewAdapter
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.databinding.DialogConnectorDetailsBinding
import net.vonforst.evmap.databinding.DialogConnectorDetailsHeaderBinding
import net.vonforst.evmap.databinding.DialogDataSourceSelectBinding
import net.vonforst.evmap.databinding.FragmentChargepriceHeaderBinding
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.model.FILTERS_DISABLED
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.MaterialDialogFragment
import java.time.Instant

class ConnectorDetailsDialog : MaterialDialogFragment() {
    private lateinit var binding: DialogConnectorDetailsBinding

    companion object {
        fun getInstance(
            chargepoint: Chargepoint,
            status: List<ChargepointStatus>?,
            evseIds: List<String>? = null,
            labels: List<String?>? = null,
            lastChange: List<Instant?>? = null
        ): ConnectorDetailsDialog {
            val dialog = ConnectorDetailsDialog()
            dialog.arguments = Bundle().apply {
                putParcelable("chargepoint", chargepoint)
                putParcelableArrayList("status", status?.let { ArrayList(status) })
                putStringArrayList("evseIds", evseIds?.let { ArrayList(it) })
                putStringArrayList("labels", labels?.let { ArrayList(it) })
                putSerializable("lastChange", lastChange?.let { ArrayList(it) })
            }
            return dialog
        }
    }

    override fun createView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogConnectorDetailsBinding.inflate(inflater, container, false)
        prefs = PreferenceDataSource(requireContext())
        return binding.root
    }

    private lateinit var prefs: PreferenceDataSource

    override fun initView(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()

        val chargepoint = BundleCompat.getParcelable(args, "chargepoint", Chargepoint::class.java)!!
        val status =
            BundleCompat.getParcelableArrayList(args, "status", ChargepointStatus::class.java)
        val evseIds = args.getStringArrayList("evseIds")
        val labels = args.getStringArrayList("labels")
        val lastChange = args.getSerializable("lastChange") as ArrayList<Instant>?

        val items = if (status != null) {
            List(chargepoint.count) { i ->
                ConnectorDetailsAdapter.ConnectorDetails(
                    status.get(i),
                    evseIds?.get(i),
                    labels?.get(i),
                    lastChange?.get(i)
                )
            }.sortedBy { it.evseId ?: it.label }
        } else emptyList()

        binding.list.apply {
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }

        val headerBinding = DataBindingUtil.inflate<DialogConnectorDetailsHeaderBinding>(
            LayoutInflater.from(context),
            R.layout.dialog_connector_details_header, binding.list, false
        )
        if (items.isEmpty()) headerBinding.divider.visibility = View.GONE

        val joinedAdapter = ConcatAdapter(
            SingleViewAdapter(headerBinding.root),
            ConnectorDetailsAdapter().apply {
                submitList(items)
            }
        )

        binding.list.adapter = joinedAdapter
        headerBinding.item = ConnectorAdapter.ChargepointWithAvailability(chargepoint, status)

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }
}