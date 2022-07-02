package net.vonforst.evmap

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.car2go.maps.model.LatLng
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialSharedAxis
import net.vonforst.evmap.fragment.MapFragmentArgs
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.navigation.NavHostFragment
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.utils.LocaleContextWrapper
import net.vonforst.evmap.utils.getLocationFromIntent


const val REQUEST_LOCATION_PERMISSION = 1
const val EXTRA_CHARGER_ID = "chargerId"
const val EXTRA_LAT = "lat"
const val EXTRA_LON = "lon"
const val EXTRA_FAVORITES = "favorites"

class MapsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
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
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()

        setContentView(R.layout.activity_maps)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.map,
                R.id.favs,
                R.id.about,
                R.id.settings
            ),
            findViewById<DrawerLayout>(R.id.drawer_layout)
        )
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setupWithNavController(navController)

        val header = navView.getHeaderView(0)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            v.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.statusBars()).top, 0, 0)
            insets
        }

        prefs = PreferenceDataSource(this)
        prefs.appStartCounter += 1

        checkPlayServices(this)

        if (!prefs.welcomeDialogShown || !prefs.dataSourceSet) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // wait for splash screen animation to finish on first start
                splashScreen.setKeepOnScreenCondition(object : SplashScreen.KeepOnScreenCondition {
                    var startTime: Long? = null

                    override fun shouldKeepOnScreen(): Boolean {
                        val st = startTime
                        if (st == null) {
                            startTime = SystemClock.uptimeMillis()
                            return true
                        } else {
                            return (SystemClock.uptimeMillis() - st) < 1000
                        }
                    }
                })
            }
            navGraph.setStartDestination(R.id.onboarding)
            navController.graph = navGraph
            return
        } else {
            navGraph.setStartDestination(R.id.map)
            navController.setGraph(navGraph, MapFragmentArgs(appStart = true).toBundle())
            var deepLink: PendingIntent? = null

            if (intent?.scheme == "geo") {
                val query = intent.data?.query?.split("=")?.get(1)
                val coords = getLocationFromIntent(intent)

                if (coords != null) {
                    val lat = coords[0]
                    val lon = coords[1]
                    deepLink = navController.createDeepLink()
                        .setGraph(R.navigation.nav_graph)
                        .setDestination(R.id.map)
                        .setArguments(MapFragmentArgs(latLng = LatLng(lat, lon)).toBundle())
                        .createPendingIntent()
                } else if (query != null && query.isNotEmpty()) {
                    deepLink = navController.createDeepLink()
                        .setGraph(R.navigation.nav_graph)
                        .setDestination(R.id.map)
                        .setArguments(MapFragmentArgs(locationName = query).toBundle())
                        .createPendingIntent()
                }
            } else if (intent?.scheme == "https" && intent?.data?.host == "www.goingelectric.de") {
                val id = intent.data?.pathSegments?.last()?.toLongOrNull()
                if (id != null) {
                    deepLink = navController.createDeepLink()
                        .setGraph(R.navigation.nav_graph)
                        .setDestination(R.id.map)
                        .setArguments(MapFragmentArgs(chargerId = id).toBundle())
                        .createPendingIntent()
                }
            } else if (intent.hasExtra(EXTRA_CHARGER_ID)) {
                deepLink = navController.createDeepLink()
                    .setDestination(R.id.map)
                    .setArguments(
                        MapFragmentArgs(
                            chargerId = intent.getLongExtra(EXTRA_CHARGER_ID, 0),
                            latLng = LatLng(
                                intent.getDoubleExtra(EXTRA_LAT, 0.0),
                                intent.getDoubleExtra(EXTRA_LON, 0.0)
                            )
                        ).toBundle()
                    )
                    .createPendingIntent()
            } else if (intent.hasExtra(EXTRA_FAVORITES)) {
                deepLink = navController.createDeepLink()
                    .setGraph(navGraph)
                    .setDestination(R.id.favs)
                    .createPendingIntent()
            }

            deepLink?.send()
        }
    }

    fun navigateTo(charger: ChargeLocation) {
        // google maps navigation
        val coord = charger.coordinates
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("google.navigation:q=${coord.lat},${coord.lng}")
        intent.`package` = "com.google.android.apps.maps"
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
        intent.data = Uri.parse(
            "geo:${coord.lat},${coord.lng}?q=${coord.lat},${coord.lng}(${
                Uri.encode(charger.name)
            })"
        )
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
        try {
            intent.launchUrl(this, Uri.parse(url))
        } catch (e: ActivityNotFoundException) {
            val cb = fragmentCallback ?: return
            Snackbar.make(
                cb.getRootView(),
                R.string.no_browser_app_found,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    fun shareUrl(url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setType("text/plain")
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(intent)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        caller.exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        caller.reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)

        // Identify the Navigation Destination
        val navDestination = navController.graph
            .find { target -> target is FragmentNavigator.Destination && pref.fragment == target.className }
        navDestination?.let { target -> navController.navigate(target.id) }
        return true
    }
}
