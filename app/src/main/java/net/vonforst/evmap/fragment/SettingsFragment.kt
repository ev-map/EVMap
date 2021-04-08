package net.vonforst.evmap.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.ListPreference
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById(R.id.toolbar) as Toolbar
        prefs = PreferenceDataSource(requireContext())

        val navController = findNavController()
        toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        val myVehiclePreference = findPreference<ListPreference>("chargeprice_my_vehicle")!!
        myVehiclePreference.isEnabled = false
        vm.vehicles.observe(viewLifecycleOwner) { res ->
            res.data?.let { cars ->
                val sortedCars = cars.sortedBy { it.brand }
                myVehiclePreference.entryValues = sortedCars.map { it.id }.toTypedArray()
                myVehiclePreference.entries =
                    sortedCars.map { "${it.brand} ${it.name}" }.toTypedArray()
                myVehiclePreference.isEnabled = true
                myVehiclePreference.summary = cars.find { it.id == prefs.chargepriceMyVehicle }
                    ?.let { "${it.brand} ${it.name}" }
            }
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
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

}