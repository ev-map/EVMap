package net.vonforst.evmap.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.View
import androidx.fragment.app.viewModels
import net.vonforst.evmap.R
import net.vonforst.evmap.ui.MultiSelectDialogPreference
import net.vonforst.evmap.viewmodel.SettingsViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory


class ChargepriceSettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = false

    private val vm: SettingsViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            SettingsViewModel(
                requireActivity().application,
                getString(R.string.chargeprice_key),
                getString(R.string.chargeprice_api_url)
            )
        }
    })

    private lateinit var myVehiclePreference: MultiSelectDialogPreference
    private lateinit var myTariffsPreference: MultiSelectDialogPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myVehiclePreference = findPreference("chargeprice_my_vehicle")!!
        myVehiclePreference.isEnabled = false
        vm.vehicles.observe(viewLifecycleOwner) { res ->
            res.data?.let { cars ->
                val sortedCars = cars.sortedBy { it.brand }
                myVehiclePreference.entryValues = sortedCars.map { it.id }.toTypedArray()
                myVehiclePreference.entries = sortedCars.map {
                    SpannableStringBuilder().apply {
                        appendLine("${it.brand} ${it.name}")
                        append(
                            it.formatSpecs(),
                            RelativeSizeSpan(0.86f),
                            Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                        )
                    }
                }.toTypedArray()
                myVehiclePreference.isEnabled = true
                updateMyVehiclesSummary()
            }
        }

        myTariffsPreference = findPreference("chargeprice_my_tariffs")!!
        myTariffsPreference.isEnabled = false
        vm.tariffs.observe(viewLifecycleOwner) { res ->
            res.data?.let { tariffs ->
                myTariffsPreference.entryValues = tariffs.map { it.id }.toTypedArray()
                myTariffsPreference.entries = tariffs.map {
                    if (!it.name.lowercase().startsWith(it.provider.lowercase())) {
                        "${it.provider} ${it.name}"
                    } else {
                        it.name
                    }
                }.toTypedArray()
                myTariffsPreference.isEnabled = true
                updateMyTariffsSummary()
            }
        }
    }

    private fun updateMyTariffsSummary() {
        myTariffsPreference.summary =
            if (prefs.chargepriceMyTariffsAll) {
                getString(R.string.chargeprice_all_tariffs_selected)
            } else {
                val n = prefs.chargepriceMyTariffs?.size ?: 0
                requireContext().resources
                    .getQuantityString(
                        R.plurals.chargeprice_some_tariffs_selected,
                        n,
                        n
                    ) + "\n" + requireContext().resources
                    .getQuantityString(R.plurals.pref_my_tariffs_summary, n)
            }
    }

    private fun updateMyVehiclesSummary() {
        vm.vehicles.value?.data?.let { cars ->
            val vehicles = cars.filter { it.id in prefs.chargepriceMyVehicles }
            val summary = vehicles.joinToString(", ") {
                "${it.brand} ${it.name}"
            }
            myVehiclePreference.summary = summary
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_chargeprice, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            "chargeprice_my_vehicle" -> {
                updateMyVehiclesSummary()
            }

            "chargeprice_my_tariffs" -> {
                updateMyTariffsSummary()
            }
        }
    }
}