package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.recyclerview.widget.LinearLayoutManager
import net.vonforst.evmap.adapter.ConnectorDetailsAdapter
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.ChargepointStatus
import net.vonforst.evmap.databinding.DialogConnectorDetailsBinding
import net.vonforst.evmap.databinding.DialogDataSourceSelectBinding
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
            status: List<ChargepointStatus>,
            evseIds: List<String>? = null,
            labels: List<String?>? = null,
            lastChange: List<Instant?>? = null
        ): ConnectorDetailsDialog {
            val dialog = ConnectorDetailsDialog()
            dialog.arguments = Bundle().apply {
                putParcelable("chargepoint", chargepoint)
                putParcelableArrayList("status", ArrayList(status))
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

        val items = List(chargepoint.count) { i ->
            ConnectorDetailsAdapter.ConnectorDetails(
                chargepoint,
                status?.get(i),
                evseIds?.get(i),
                labels?.get(i),
                lastChange?.get(i)
            )
        }.sortedBy { it.evseId ?: it.label }

        binding.list.apply {
            adapter = ConnectorDetailsAdapter().apply {
                submitList(items)
            }
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }
}