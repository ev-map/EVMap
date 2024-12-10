package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.vonforst.evmap.databinding.DialogDataSourceSelectBinding
import net.vonforst.evmap.model.FILTERS_DISABLED
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.MaterialDialogFragment

class DataSourceSelectDialog : MaterialDialogFragment() {
    private lateinit var binding: DialogDataSourceSelectBinding
    var okListener: ((String) -> Unit)? = null

    companion object {
        fun getInstance(
            cancelEnabled: Boolean
        ): DataSourceSelectDialog {
            val dialog = DataSourceSelectDialog()
            dialog.arguments = args(cancelEnabled)
            return dialog
        }

        fun args(cancelEnabled: Boolean) = Bundle().apply {
            putBoolean("cancel_enabled", cancelEnabled)
        }
    }

    override fun createView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogDataSourceSelectBinding.inflate(inflater, container, false)
        prefs = PreferenceDataSource(requireContext())
        return binding.root
    }

    override fun onStart() {
        super.onStart()

        setFullSize()
    }

    private lateinit var prefs: PreferenceDataSource

    override fun initView(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        binding.btnCancel.visibility =
            if (args.getBoolean("cancel_enabled")) View.VISIBLE else View.GONE

        if (prefs.dataSourceSet) {
            when (prefs.dataSource) {
                "goingelectric" -> binding.rgDataSource.rbGoingElectric.isChecked = true
                "openchargemap" -> binding.rgDataSource.rbOpenChargeMap.isChecked = true
                "openstreetmap" -> binding.rgDataSource.rbOpenStreetMap.isChecked = true
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        binding.btnOK.setOnClickListener {
            val result = if (binding.rgDataSource.rbGoingElectric.isChecked) {
                "goingelectric"
            } else if (binding.rgDataSource.rbOpenChargeMap.isChecked) {
                "openchargemap"
            } else if (binding.rgDataSource.rbOpenStreetMap.isChecked) {
                "openstreetmap"
            } else {
                return@setOnClickListener
            }
            prefs.dataSource = result
            prefs.filterStatus = FILTERS_DISABLED
            okListener?.let { listener ->
                listener(result)
            }
            prefs.dataSourceSet = true
            dismiss()
        }
    }
}