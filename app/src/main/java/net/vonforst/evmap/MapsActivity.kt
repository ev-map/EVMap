package net.vonforst.evmap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import net.vonforst.evmap.api.goingelectric.ChargeLocation

const val REQUEST_LOCATION_PERMISSION = 1

class MapsActivity : AppCompatActivity() {
    interface FragmentCallback {
        fun getRootView(): View
        fun goBack(): Boolean
    }

    private var reenterState: Bundle? = null
    private lateinit var navController: NavController
    lateinit var appBarConfiguration: AppBarConfiguration
    var fragmentCallback: FragmentCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_maps)

        navController = findNavController(R.id.nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.map,
                R.id.favs,
                R.id.about
            ),
            findViewById<DrawerLayout>(R.id.drawer_layout)
        )
        findViewById<NavigationView>(R.id.nav_view).setupWithNavController(navController)
    }

    override fun onBackPressed() {
        val didGoBack = fragmentCallback?.goBack() ?: false
        if (!didGoBack) super.onBackPressed()
    }

    fun navigateTo(charger: ChargeLocation) {
        val intent = Intent(Intent.ACTION_VIEW)
        val coord = charger.coordinates

        // google maps navigation
        intent.data = Uri.parse("google.navigation:q=${coord.lat},${coord.lng}")
        val pm = packageManager
        if (intent.resolveActivity(pm) != null) {
            startActivity(intent);
        } else {
            // fallback: generic geo intent
            intent.data = Uri.parse("geo:${coord.lat},${coord.lng}")
            if (intent.resolveActivity(pm) != null) {
                startActivity(intent);
            } else {
                val cb = fragmentCallback ?: return
                Snackbar.make(
                    cb.getRootView(),
                    R.string.no_maps_app_found,
                    Snackbar.LENGTH_SHORT
                )
            }
        }
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
