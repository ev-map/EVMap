package net.vonforst.evmap.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.updateNightMode
import net.vonforst.evmap.viewmodel.SettingsViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory


class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: PreferenceDataSource

    private val vm: SettingsViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            SettingsViewModel(
                requireActivity().application,
                getString(R.string.chargeprice_key)
            )
        }
    })

    private lateinit var myVehiclePreference: MultiSelectListPreference
    private lateinit var myTariffsPreference: MultiSelectListPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceDataSource(requireContext())

        myVehiclePreference = findPreference("chargeprice_my_vehicle")!!
        myVehiclePreference.isEnabled = false
        vm.vehicles.observe(viewLifecycleOwner) { res ->
            res.data?.let { cars ->
                val sortedCars = cars.sortedBy { it.brand }
                myVehiclePreference.entryValues = sortedCars.map { it.id }.toTypedArray()
                myVehiclePreference.entries =
                    sortedCars.map { "${it.brand} ${it.name}" }.toTypedArray()
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
                    ) + "\n" + getString(R.string.pref_my_tariffs_summary)
            }
    }

    private fun updateMyVehiclesSummary() {
        vm.vehicles.value?.data?.let { cars ->
            val vehicles = cars.filter { it.id in prefs.chargepriceMyVehicles }
            val summary = vehicles.map {
                "${it.brand} ${it.name}"
            }.joinToString(", ")
            myVehiclePreference.summary = summary
            // TODO: prefs.chargepriceMyVehicleDcChargeports = it.dcChargePorts
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "language" -> {
                activity?.let {
                    it.finish();
                    it.startActivity(it.intent);
                }
            }
            "darkmode" -> {
                updateNightMode(prefs)
            }
            "chargeprice_my_vehicle" -> {
                updateMyVehiclesSummary()
            }
            "chargeprice_my_tariffs" -> {
                updateMyTariffsSummary()
            }
            "search_provider" -> {
                if (prefs.searchProvider == "google") {
                    Toast.makeText(context, R.string.pref_search_provider_info, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        val navController = findNavController()
        val toolbar = requireView().findViewById(R.id.toolbar) as Toolbar
        toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )
    }

    override fun onPause() {
        preferenceManager.sharedPreferences
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

}