package net.vonforst.evmap.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import net.vonforst.evmap.R
import net.vonforst.evmap.viewmodel.SettingsViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory

class DataSettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = false

    private val vm: SettingsViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            SettingsViewModel(
                requireActivity().application,
                getString(R.string.chargeprice_key)
            )
        }
    })

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_data, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "search_provider" -> {
                if (prefs.searchProvider == "google") {
                    Snackbar.make(
                        requireView(),
                        R.string.pref_search_provider_info,
                        Snackbar.LENGTH_INDEFINITE
                    ).apply {
                        setAction(R.string.ok) {}
                        this.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                            ?.apply {
                                maxLines = 6
                            }
                    }
                        .show()
                }
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            "search_delete_recent" -> {
                Snackbar.make(
                    requireView(),
                    R.string.deleted_recent_search_results,
                    Snackbar.LENGTH_LONG
                )
                    .show()
                vm.deleteRecentSearchResults()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }
}