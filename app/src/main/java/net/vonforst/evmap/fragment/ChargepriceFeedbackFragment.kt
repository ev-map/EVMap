package net.vonforst.evmap.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.FragmentChargepriceFeedbackBinding
import net.vonforst.evmap.viewmodel.ChargepriceFeedbackViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory

class ChargepriceFeedbackFragment : Fragment() {

    private lateinit var binding: FragmentChargepriceFeedbackBinding
    private val vm: ChargepriceFeedbackViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            ChargepriceFeedbackViewModel(
                requireActivity().application,
                getString(R.string.chargeprice_key),
                getString(R.string.chargeprice_api_url)
            )
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fragmentArgs: ChargepriceFeedbackFragmentArgs by navArgs()
        vm.feedbackType.value = fragmentArgs.feedbackType
        vm.charger.value = fragmentArgs.charger
        vm.vehicle.value = fragmentArgs.vehicle
        vm.chargePrices.value = fragmentArgs.chargePrices?.toList()
        vm.batteryRange.value = fragmentArgs.batteryRange?.toList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_chargeprice_feedback, container, false
        )
        binding.lifecycleOwner = this
        binding.vm = vm
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )
        binding.tariffSpinner.setAdapter(
            ArrayAdapter<String>(
                requireContext(),
                R.layout.item_simple_multiline,
                R.id.text,
                mutableListOf()
            )
        )
    }
}