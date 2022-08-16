package net.vonforst.evmap.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.ChargepriceAdapter
import net.vonforst.evmap.adapter.CheckableChargepriceCarAdapter
import net.vonforst.evmap.adapter.CheckableConnectorAdapter
import net.vonforst.evmap.api.chargeprice.ChargepriceCar
import net.vonforst.evmap.api.equivalentPlugTypes
import net.vonforst.evmap.databinding.FragmentChargepriceBinding
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.viewmodel.ChargepriceViewModel
import net.vonforst.evmap.viewmodel.Status
import net.vonforst.evmap.viewmodel.savedStateViewModelFactory
import java.text.NumberFormat

class ChargepriceFragment : Fragment() {
    private lateinit var binding: FragmentChargepriceBinding
    private var connectionErrorSnackbar: Snackbar? = null

    private val vm: ChargepriceViewModel by viewModels(factoryProducer = {
        savedStateViewModelFactory { state ->
            ChargepriceViewModel(
                requireActivity().application,
                getString(R.string.chargeprice_key),
                state
            )
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = MaterialContainerTransform()

        if (savedInstanceState == null) {
            val prefs = PreferenceDataSource(requireContext())
            prefs.chargepriceCounter += 1
            if ((prefs.chargepriceCounter - 30).mod(50) == 0) {
                showDonationDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reloadPrefs()
    }

    private fun showDonationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.chargeprice_donation_dialog_title)
            .setMessage(R.string.chargeprice_donation_dialog_detail)
            .setNegativeButton(R.string.ok) { di, _ ->
                di.cancel()
            }
            .setPositiveButton(R.string.donate) { di, _ ->
                di.dismiss()
                findNavController().navigate(R.id.action_chargeprice_to_donateFragment)
            }
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        val fragmentArgs: ChargepriceFragmentArgs by navArgs()
        val charger = fragmentArgs.charger
        val dataSource = fragmentArgs.dataSource
        vm.charger.value = charger
        vm.dataSource.value = dataSource
        if (vm.chargepoint.value == null) {
            vm.chargepoint.value = charger.chargepointsMerged.get(0)
        }

        val vehicleAdapter = CheckableChargepriceCarAdapter()
        binding.vehicleSelection.adapter = vehicleAdapter
        val vehicleObserver: Observer<ChargepriceCar> = Observer {
            vehicleAdapter.setCheckedItem(it)
        }
        vm.vehicle.observe(viewLifecycleOwner, vehicleObserver)
        vehicleAdapter.onCheckedItemChangedListener = {
            vm.vehicle.removeObserver(vehicleObserver)
            vm.vehicle.value = it
            vm.vehicle.observe(viewLifecycleOwner, vehicleObserver)
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
        vm.myTariffs.observe(viewLifecycleOwner) {
            chargepriceAdapter.myTariffs = it
        }
        vm.myTariffsAll.observe(viewLifecycleOwner) {
            chargepriceAdapter.myTariffsAll = it
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

        vm.vehicleCompatibleConnectors.observe(viewLifecycleOwner) { plugs ->
            connectorsAdapter.enabledConnectors =
                plugs?.flatMap { plug -> equivalentPlugTypes(plug) }
        }

        binding.connectorsList.apply {
            adapter = connectorsAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.imgChargepriceLogo.setOnClickListener {
            (requireActivity() as MapsActivity).openUrl("https://www.chargeprice.app/?poi_id=${charger.id}&poi_source=${dataSource}")
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_chargeprice_to_chargepriceSettingsFragment)
        }

        binding.batteryRange.setLabelFormatter { value: Float ->
            val fmt = NumberFormat.getNumberInstance()
            fmt.maximumFractionDigits = 0
            fmt.format(value.toDouble()) + "%"
        }
        binding.batteryRange.setOnTouchListener { _: View, motionEvent: MotionEvent ->
            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_DOWN -> vm.batteryRangeSliderDragging.value = true
                MotionEvent.ACTION_UP -> vm.batteryRangeSliderDragging.value = false
            }
            false
        }

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_help -> {
                    (activity as? MapsActivity)?.openUrl(getString(R.string.chargeprice_faq_link))
                    true
                }
                else -> false
            }
        }

        vm.chargePricesForChargepoint.observe(viewLifecycleOwner) { res ->
            when (res?.status) {
                Status.ERROR -> {
                    if (vm.vehicle.value == null) return@observe
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

        // Workaround for AndroidX bug: https://github.com/material-components/material-components-android/issues/1984
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.windowBackground))
    }

}
