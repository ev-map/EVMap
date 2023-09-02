package net.vonforst.evmap.fragment.preference

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import net.vonforst.evmap.R

class DeveloperSettingsFragment : BaseSettingsFragment() {
    override val isTopLevel = false
    private val locationManager: LocationManager by lazy {
        requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_developer, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val locationPref = findPreference<Preference>("location_status")!!
        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        locationPref.summary = buildString {
            append("Coarse location permission: ")
            appendLine(if (coarseGranted) "granted" else "not granted")
            append("Fine location permission: ")
            appendLine(if (fineGranted) "granted" else "not granted")
            appendLine()

            if (coarseGranted) {
                append("Last network location: ")
                appendLine(printLocation(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)))
            }
            if (fineGranted) {
                append("Last GPS location: ")
                appendLine(printLocation(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.allProviders.contains(
                        LocationManager.FUSED_PROVIDER
                    )
                ) {
                    append("Last fused location: ")
                    append(printLocation(locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)))
                } else {
                    append("System's fused location provider not available")
                }
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            "disable_developer_mode" -> {
                prefs.developerModeEnabled = false
                Toast.makeText(
                    requireContext(),
                    getString(R.string.developer_mode_disabled),
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().popBackStack()
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }


    fun printLocation(location: Location?): String {
        if (location == null) return "not available"

        return buildString {
            append("%.4f".format(location.latitude))
            append(",")
            append("%.4f".format(location.longitude))
            append(" (")
            append(DateUtils.getRelativeTimeSpanString(location.time))
            append(")")
        }
    }
}