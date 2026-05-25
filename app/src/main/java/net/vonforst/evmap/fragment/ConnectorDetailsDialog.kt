package net.vonforst.evmap.fragment

import android.content.res.ColorStateList
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import net.vonforst.evmap.adapter.ConnectorDetailsAdapter
import net.vonforst.evmap.adapter.SingleViewAdapter
import net.vonforst.evmap.api.iconForPlugType
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.databinding.DialogConnectorDetailsBinding
import net.vonforst.evmap.databinding.DialogConnectorDetailsHeaderBinding
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.ui.availabilityColor
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.goneUnless
import java.util.Locale

class ConnectorDetailsDialog(
    binding: DialogConnectorDetailsBinding,
    context: Context,
    onClose: () -> Unit
) {
    private var headerBinding_: DialogConnectorDetailsHeaderBinding? = null
    private val headerBinding get() = headerBinding_!!
    private val detailsAdapter = ConnectorDetailsAdapter()

    init {
        binding.list.apply {
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }
        headerBinding_ = DialogConnectorDetailsHeaderBinding.inflate(
            LayoutInflater.from(context),
            binding.list,
            false
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

        val context = headerBinding.root.context
        val locale = Locale.getDefault()
        val provider = context.stringProvider()

        headerBinding.imageView.setImageResource(iconForPlugType(cp.type))
        headerBinding.imageView.contentDescription = cp.type
        headerBinding.imageView.imageTintList =
            ColorStateList.valueOf(availabilityColor(cpStatus, context))

        headerBinding.textView5.text = String.format(locale, "x %d", cp.count)
        goneUnless(headerBinding.textView5, cpStatus == null)

        if (cpStatus != null) {
            headerBinding.textView7.text = String.format(
                locale,
                "%s/%d",
                availabilityText(cpStatus),
                cp.count
            )
            headerBinding.textView7.backgroundTintList =
                ColorStateList.valueOf(availabilityColor(cpStatus, context))
        }
        goneUnless(headerBinding.textView7, cpStatus != null)

        val name = nameForPlugType(provider, cp.type)
        headerBinding.textView6.text = if (cp.hasKnownPower()) {
            "$name - ${cp.formatPower(locale)}"
        } else {
            name
        }

        headerBinding.textView8.text = cp.formatVoltageAndCurrent()
        goneUnless(headerBinding.textView8, cp.hasKnownVoltageAndCurrent())
    }

    fun onDestroy() {
        headerBinding_ = null
    }
}