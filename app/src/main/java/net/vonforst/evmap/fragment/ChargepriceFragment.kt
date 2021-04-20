package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.ChargepriceAdapter
import net.vonforst.evmap.adapter.CheckableConnectorAdapter
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.databinding.FragmentChargepriceBinding
import net.vonforst.evmap.viewmodel.ChargepriceViewModel
import net.vonforst.evmap.viewmodel.Status
import net.vonforst.evmap.viewmodel.viewModelFactory
import java.text.NumberFormat

class ChargepriceFragment : DialogFragment() {
    private lateinit var binding: FragmentChargepriceBinding
    private var connectionErrorSnackbar: Snackbar? = null

    private val vm: ChargepriceViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            ChargepriceViewModel(
                requireActivity().application,
                getString(R.string.chargeprice_key)
            )
        }
    })

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog?.window?.attributes?.windowAnimations = R.style.ChargepriceDialogAnimation
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_chargeprice, container, false
        )
        binding.lifecycleOwner = this
        binding.vm = vm

        binding.toolbar.inflateMenu(R.menu.chargeprice)
        binding.toolbar.setTitle(R.string.chargeprice_title)

        return binding.root
    }

    override fun onStart() {
        super.onStart()

        // dialog with 95% screen height
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.95).toInt()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById(R.id.toolbar) as Toolbar

        val navController = findNavController()
        toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        val jsonAdapter = GoingElectricApi.moshi.adapter(ChargeLocation::class.java)
        val charger = jsonAdapter.fromJson(requireArguments().getString(ARG_CHARGER)!!)!!
        vm.charger.value = charger
        if (vm.chargepoint.value == null) {
            vm.chargepoint.value = charger.chargepointsMerged.get(0)
        }

        val chargepriceAdapter = ChargepriceAdapter().apply {
            onClickListener = {
                (requireActivity() as MapsActivity).openUrl(it.url)
            }
        }
        binding.chargePricesList.apply {
            adapter = chargepriceAdapter
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context, LinearLayoutManager.VERTICAL
                )
            )
        }
        vm.chargepriceMetaForChargepoint.observe(viewLifecycleOwner) {
            chargepriceAdapter.meta = it?.data
        }

        val connectorsAdapter = CheckableConnectorAdapter()

        val observer: Observer<Chargepoint> = Observer {
            connectorsAdapter.setCheckedItem(it)
        }
        vm.chargepoint.observe(viewLifecycleOwner, observer)
        connectorsAdapter.onCheckedItemChangedListener = {
            vm.chargepoint.removeObserver(observer)
            vm.chargepoint.value = it
            vm.chargepoint.observe(viewLifecycleOwner, observer)
        }

        vm.vehicleCompatibleConnectors.observe(viewLifecycleOwner) {
            connectorsAdapter.enabledConnectors = it
        }

        binding.connectorsList.apply {
            adapter = connectorsAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.imgChargepriceLogo.setOnClickListener {
            (requireActivity() as MapsActivity).openUrl("https://www.chargeprice.app/?poi_id=${charger.id}&poi_source=going_electric")
        }

        binding.btnSettings.setOnClickListener {
            navController.navigate(R.id.action_chargeprice_to_settingsFragment)
        }

        binding.batteryRange.setLabelFormatter { value: Float ->
            val fmt = NumberFormat.getNumberInstance()
            fmt.maximumFractionDigits = 0
            fmt.format(value.toDouble())
        }

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_close -> {
                    dismiss()
                    true
                }
                else -> false
            }
        }

        vm.chargePricesForChargepoint.observe(viewLifecycleOwner) { res ->
            when (res?.status) {
                Status.ERROR -> {
                    connectionErrorSnackbar?.dismiss()
                    connectionErrorSnackbar = Snackbar
                        .make(
                            view,
                            R.string.chargeprice_connection_error,
                            Snackbar.LENGTH_INDEFINITE
                        )
                        .setAction(R.string.retry) {
                            connectionErrorSnackbar?.dismiss()
                            vm.loadPrices()
                        }
                    connectionErrorSnackbar!!.show()
                }
                Status.SUCCESS, null -> {
                    connectionErrorSnackbar?.dismiss()
                }
                Status.LOADING -> {
                }
            }
        }
    }

    companion object {
        val ARG_CHARGER = "charger"

        fun showCharger(charger: ChargeLocation): Bundle {
            return Bundle().apply {
                putString(
                    ARG_CHARGER,
                    GoingElectricApi.moshi.adapter(ChargeLocation::class.java).toJson(charger)
                )
            }
        }
    }
}