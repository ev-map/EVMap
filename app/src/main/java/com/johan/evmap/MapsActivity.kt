package com.johan.evmap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.johan.evmap.api.ChargeLocation

const val REQUEST_LOCATION_PERMISSION = 1


class MapsActivity : AppCompatActivity() {
    interface FragmentCallback {
        fun getRootView(): CoordinatorLayout
    }

    private lateinit var navController: NavController
    lateinit var appBarConfiguration: AppBarConfiguration
    var fragmentCallback: FragmentCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_maps)

        //ActivityCompat.setExitSharedElementCallback(this, exitElementCallback)
        //setSupportActionBar(binding.toolbar)
        //title = ""

        navController = findNavController(R.id.nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.map,
                R.id.favs
            ),
            findViewById<DrawerLayout>(R.id.drawer_layout)
        )
        findViewById<NavigationView>(R.id.nav_view).setupWithNavController(navController)
    }

    override fun onBackPressed() {

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


    /*private val exitElementCallback = object : SharedElementCallback() {
        override fun onMapSharedElements(
            names: MutableList<String>,
            sharedElements: MutableMap<String, View>
        ) {
            if (reenterState != null) {
                val startingPosition = reenterState!!.getInt(MapFragment.EXTRA_STARTING_GALLERY_POSITION)
                val currentPosition = reenterState!!.getInt(MapFragment.EXTRA_CURRENT_GALLERY_POSITION)
                if (startingPosition != currentPosition) {
                    // Current element has changed, need to override previous exit transitions
                    val newTransitionName = galleryTransitionName(currentPosition)
                    val newSharedElement =
                        binding.gallery.findViewHolderForAdapterPosition(currentPosition)?.itemView
                    if (newSharedElement != null) {
                        names.clear()
                        names.add(newTransitionName)

                        sharedElements.clear()
                        sharedElements[newTransitionName] = newSharedElement
                    }
                }
                reenterState = null
            }
        }
    }*/

    /*override fun onActivityReenter(resultCode: Int, data: Intent) {
        // returning to gallery
        super.onActivityReenter(resultCode, data)
        reenterState = Bundle(data.extras)
        reenterState?.let {
            val startingPosition = it.getInt(MapFragment.EXTRA_STARTING_GALLERY_POSITION)
            val currentPosition = it.getInt(MapFragment.EXTRA_CURRENT_GALLERY_POSITION)
            if (startingPosition != currentPosition) binding.gallery.scrollToPosition(
                currentPosition
            )
            ActivityCompat.postponeEnterTransition(this)

            binding.gallery.viewTreeObserver.addOnPreDrawListener(object :
                ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    binding.gallery.viewTreeObserver.removeOnPreDrawListener(this)
                    ActivityCompat.startPostponedEnterTransition(this@MapsActivity)
                    return true
                }
            })
        }
    }*/
}
