package net.vonforst.evmap.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.storage.EncryptedPreferenceDataStore
import net.vonforst.evmap.storage.PreferenceDataSource

abstract class BaseSettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    protected lateinit var prefs: PreferenceDataSource
    protected lateinit var encryptedPrefs: EncryptedPreferenceDataStore
    protected abstract val isTopLevel: Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferenceDataSource(requireContext())
        encryptedPrefs = EncryptedPreferenceDataStore(requireContext())
        super.onCreate(savedInstanceState)

        if (isTopLevel) {
            enterTransition = MaterialFadeThrough()
            exitTransition = MaterialFadeThrough()
        } else {
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
            returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            WindowInsetsCompat.CONSUMED
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        // Workaround for AndroidX bug: https://github.com/material-components/material-components-android/issues/1984
        view.setBackgroundColor(MaterialColors.getColor(view, android.R.attr.windowBackground))
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }
}