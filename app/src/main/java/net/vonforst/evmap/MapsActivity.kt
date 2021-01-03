package net.vonforst.evmap

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.utils.LocaleContextWrapper


const val REQUEST_LOCATION_PERMISSION = 1

class MapsActivity : AppCompatActivity() {
    interface FragmentCallback {
        fun getRootView(): View
    }

    private var reenterState: Bundle? = null
    private lateinit var navController: NavController
    lateinit var appBarConfiguration: AppBarConfiguration
    var fragmentCallback: FragmentCallback? = null
    private lateinit var prefs: PreferenceDataSource

    override fun attachBaseContext(newBase: Context) {
        return super.attachBaseContext(
            LocaleContextWrapper.wrap(
                newBase, PreferenceDataSource(newBase).language
            )
        );
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // set theme to AppTheme to end launch screen
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_maps)

        navController = findNavController(R.id.nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.map,
                R.id.favs,
                R.id.about,
                R.id.settings
            ),
            findViewById<DrawerLayout>(R.id.drawer_layout)
        )
        findViewById<NavigationView>(R.id.nav_view).setupWithNavController(navController)

        prefs = PreferenceDataSource(this)

        checkPlayServices(this)
    }

    fun navigateTo(charger: ChargeLocation) {
        // google maps navigation
        val coord = charger.coordinates
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("google.navigation:q=${coord.lat},${coord.lng}")
        if (prefs.navigateUseMaps && intent.resolveActivity(packageManager) != null) {
            startActivity(intent);
        } else {
            // fallback: generic geo intent
            showLocation(charger)
        }
    }

    fun showLocation(charger: ChargeLocation) {
        val coord = charger.coordinates
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("geo:0,0?q=${coord.lat},${coord.lng}(${charger.name})")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent);
        } else {
            val cb = fragmentCallback ?: return
            Snackbar.make(
                cb.getRootView(),
                R.string.no_maps_app_found,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    fun openUrl(url: String) {
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
                    .build()
            )
            .build()
        intent.launchUrl(this, Uri.parse(url))
    }

    fun shareUrl(url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setType("text/plain")
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(intent)
    }
}
