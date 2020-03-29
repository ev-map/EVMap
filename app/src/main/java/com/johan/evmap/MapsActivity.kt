package com.johan.evmap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.SharedElementCallback
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.johan.evmap.adapter.ConnectorAdapter
import com.johan.evmap.adapter.DetailAdapter
import com.johan.evmap.adapter.GalleryAdapter
import com.johan.evmap.adapter.galleryTransitionName
import com.johan.evmap.api.*
import com.johan.evmap.databinding.ActivityMapsBinding
import com.johan.evmap.ui.ClusterIconGenerator
import com.johan.evmap.ui.getBitmapDescriptor
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

const val REQUEST_LOCATION_PERMISSION = 1

class MapsActivityViewModel : ViewModel() {
    val chargepoints: MutableLiveData<List<ChargepointListItem>> by lazy {
        MutableLiveData<List<ChargepointListItem>>().apply {
            value = emptyList()
        }
    }
    val charger: MutableLiveData<ChargeLocation> by lazy {
        MutableLiveData<ChargeLocation>()
    }
    val myLocationEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }
}

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMapsBinding
    private var map: GoogleMap? = null
    private lateinit var api: GoingElectricApi
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val vm: MapsActivityViewModel by viewModels()
    private var markers: Map<Marker, ChargeLocation> = emptyMap()
    private var clusterMarkers: List<Marker> = emptyList()
    private lateinit var bottomSheetBehavior: BottomSheetBehaviorGoogleMapsLike<View>

    private var reenterState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        api = GoingElectricApi.create(getString(R.string.goingelectric_key))

        binding = DataBindingUtil.setContentView(this, R.layout.activity_maps)
        binding.lifecycleOwner = this
        binding.vm = vm

        ActivityCompat.setExitSharedElementCallback(this, exitElementCallback)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupAdapters()

        bottomSheetBehavior = BottomSheetBehaviorGoogleMapsLike.from(binding.bottomSheet)

        vm.charger.observe(this, object : Observer<ChargeLocation> {
            var previousCharger = vm.charger.value

            override fun onChanged(charger: ChargeLocation?) {
                if (charger != null) {
                    if (previousCharger == null ||
                        previousCharger!!.id != charger.id
                    ) {
                        bottomSheetBehavior.state =
                            BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
                        loadChargerDetails()
                    }
                } else {
                    bottomSheetBehavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
                }
                previousCharger = charger
            }
        })
        vm.chargepoints.observe(this, Observer<List<ChargepointListItem>> {
            updateMap(it)
        })

        binding.fabLocate.setOnClickListener {
            if (!hasLocationPermission()) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
                )
            } else {
                enableLocation(true)
            }
        }
        binding.fabDirections.setOnClickListener {
            val charger = vm.charger.value
            if (charger != null) {
                val intent = Intent(Intent.ACTION_VIEW)
                val coord = charger.coordinates

                // google maps navigation
                intent.data = Uri.parse("google.navigation:q=${coord.lat},${coord.lng}")
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent);
                } else {
                    // fallback: generic geo intent
                    intent.data = Uri.parse("geo:${coord.lat},${coord.lng}")
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent);
                    } else {
                        Snackbar.make(
                            binding.root,
                            R.string.no_maps_app_found,
                            Snackbar.LENGTH_SHORT
                        )
                    }
                }
            }
        }
        binding.detailView.goingelectricButton.setOnClickListener {
            val charger = vm.charger.value
            if (charger != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https:${charger.url}"))
                startActivity(intent)
            }
        }
    }

    private fun setupAdapters() {
        val galleryClickListener = object : GalleryAdapter.ItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                val photos = vm.charger.value?.photos ?: return
                val intent = Intent(this@MapsActivity, GalleryActivity::class.java).apply {
                    putExtra(GalleryActivity.EXTRA_PHOTOS, ArrayList<ChargerPhoto>(photos))
                    putExtra(GalleryActivity.EXTRA_POSITION, position)
                }
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    this@MapsActivity, view, view.transitionName
                )
                startActivity(intent, options.toBundle())
            }
        }

        binding.gallery.apply {
            adapter = GalleryAdapter(this@MapsActivity, galleryClickListener)
            layoutManager =
                LinearLayoutManager(this@MapsActivity, LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(DividerItemDecoration(
                this@MapsActivity, LinearLayoutManager.HORIZONTAL
            ).apply {
                setDrawable(getDrawable(R.drawable.gallery_divider)!!)
            })
        }

        binding.detailView.connectors.apply {
            adapter = ConnectorAdapter()
            layoutManager =
                LinearLayoutManager(this@MapsActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.detailView.details.apply {
            adapter = DetailAdapter()
            layoutManager =
                LinearLayoutManager(this@MapsActivity, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    this@MapsActivity,
                    LinearLayoutManager.VERTICAL
                )
            )
        }
    }


    override fun onMapReady(map: GoogleMap) {
        this.map = map
        map.setOnCameraIdleListener {
            loadChargepoints()
        }
        map.setOnMarkerClickListener { marker ->
            when (marker) {
                in markers -> {
                    vm.charger.value = markers[marker]
                    true
                }
                in clusterMarkers -> {
                    val newZoom = map.cameraPosition.zoom + 2
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, newZoom))
                    true
                }
                else -> false
            }
        }
        map.setOnMapClickListener {
            vm.charger.value = null
        }

        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        map.setMapStyle(
            if (mode == Configuration.UI_MODE_NIGHT_YES) {
                MapStyleOptions.loadRawResourceStyle(this, R.raw.maps_night_mode)
            } else null
        )


        if (hasLocationPermission()) {
            enableLocation(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation(animate: Boolean) {
        val map = this.map ?: return
        map.isMyLocationEnabled = true
        vm.myLocationEnabled.value = true
        map.uiSettings.isMyLocationButtonEnabled = false
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                if (animate) {
                    val camUpdate = CameraUpdateFactory.newLatLng(latLng)
                    map.animateCamera(camUpdate)
                } else {
                    val camUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 13f)
                    map.moveCamera(camUpdate)
                }
            }
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun loadChargepoints() {
        val map = this.map ?: return
        val bounds = map.projection.visibleRegion.latLngBounds
        api.getChargepoints(
            bounds.southwest.latitude, bounds.southwest.longitude,
            bounds.northeast.latitude, bounds.northeast.longitude,
            clustering = map.cameraPosition.zoom < 12, zoom = map.cameraPosition.zoom,
            clusterDistance = 70
        ).enqueue(object : Callback<ChargepointList> {
            override fun onFailure(call: Call<ChargepointList>, t: Throwable) {
                //TODO: show error message
                t.printStackTrace()
            }

            override fun onResponse(
                call: Call<ChargepointList>,
                response: Response<ChargepointList>
            ) {
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    //TODO: show error message
                    return
                }

                vm.chargepoints.value = response.body()!!.chargelocations
            }
        })
    }

    private fun loadChargerDetails() {
        val id = vm.charger.value?.id ?: return
        api.getChargepointDetail(id).enqueue(object : Callback<ChargepointList> {
            override fun onFailure(call: Call<ChargepointList>, t: Throwable) {
                //TODO: show error message
                t.printStackTrace()
            }

            override fun onResponse(
                call: Call<ChargepointList>,
                response: Response<ChargepointList>
            ) {
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    //TODO: show error message
                    return
                }

                vm.charger.value = response.body()!!.chargelocations[0] as ChargeLocation
            }
        })
    }

    private fun updateMap(chargepoints: List<ChargepointListItem>) {
        val map = this.map ?: return
        markers.keys.forEach { it.remove() }
        clusterMarkers.forEach { it.remove() }

        val iconGenerator = ClusterIconGenerator(this)
        val clusters = chargepoints.filterIsInstance<ChargeLocationCluster>()
        val chargers = chargepoints.filterIsInstance<ChargeLocation>()

        markers = chargers.map { charger ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(charger.coordinates.lat, charger.coordinates.lng))
                    .icon(
                        getBitmapDescriptor(
                            R.drawable.ic_map_marker_charging, when {
                                charger.maxPower >= 100 -> R.color.charger_100kw
                                charger.maxPower >= 43 -> R.color.charger_43kw
                                charger.maxPower >= 20 -> R.color.charger_20kw
                                charger.maxPower >= 11 -> R.color.charger_11kw
                                else -> R.color.charger_low
                            }, this
                        )
                    )
            ) to charger
        }.toMap()
        clusterMarkers = clusters.map { cluster ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(cluster.coordinates.lat, cluster.coordinates.lng))
                    .icon(BitmapDescriptorFactory.fromBitmap(iconGenerator.makeIcon(cluster.clusterCount.toString())))
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    enableLocation(true)
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior.state != BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED &&
            bottomSheetBehavior.state != BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
        ) {
            bottomSheetBehavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
        } else if (bottomSheetBehavior.state == BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED) {
            vm.charger.value = null
        } else {
            super.onBackPressed()
        }
    }

    private val exitElementCallback = object : SharedElementCallback() {
        override fun onMapSharedElements(
            names: MutableList<String>,
            sharedElements: MutableMap<String, View>
        ) {
            if (reenterState != null) {
                val startingPosition = reenterState!!.getInt(EXTRA_STARTING_GALLERY_POSITION)
                val currentPosition = reenterState!!.getInt(EXTRA_CURRENT_GALLERY_POSITION)
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
    }

    override fun onActivityReenter(resultCode: Int, data: Intent) {
        // returning to gallery
        super.onActivityReenter(resultCode, data)
        reenterState = Bundle(data.extras)
        reenterState?.let {
            val startingPosition = it.getInt(EXTRA_STARTING_GALLERY_POSITION)
            val currentPosition = it.getInt(EXTRA_CURRENT_GALLERY_POSITION)
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
    }

    companion object {
        const val EXTRA_STARTING_GALLERY_POSITION = "extra_starting_item_position"
        const val EXTRA_CURRENT_GALLERY_POSITION = "extra_current_item_position"
    }
}
