package com.johan.evmap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.ui.IconGenerator
import com.johan.evmap.adapter.ConnectorAdapter
import com.johan.evmap.adapter.GalleryAdapter
import com.johan.evmap.api.*
import com.johan.evmap.databinding.ActivityMapsBinding
import com.johan.evmap.ui.getBitmapDescriptor
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

const val REQUEST_LOCATION_PERMISSION = 1

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMapsBinding
    private lateinit var map: GoogleMap
    private lateinit var api: GoingElectricApi
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var chargepoints: List<ChargepointListItem> = emptyList()
    private var markers: Map<Marker, ChargeLocation> = emptyMap()
    private var clusterMarkers: List<Marker> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_maps)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        api = GoingElectricApi.create(getString(R.string.goingelectric_key))

        val behavior = BottomSheetBehaviorGoogleMapsLike.from(binding.bottomSheet)

        binding.gallery.adapter = GalleryAdapter(this)
        binding.gallery.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.gallery.addItemDecoration(DividerItemDecoration(
            this, LinearLayoutManager.HORIZONTAL
        ).apply {
            setDrawable(getDrawable(R.drawable.gallery_divider)!!)
        })

        binding.detailView.connectors.adapter = ConnectorAdapter()
        binding.detailView.connectors.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        binding.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            var previousCharger = binding.charger

            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                if (propertyId == BR.charger) {
                    if (binding.charger != null) {
                        val charger = binding.charger!!

                        if (previousCharger == null ||
                            previousCharger!!.id != charger.id
                        ) {
                            behavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
                            loadChargerDetails()
                        }
                    } else {
                        behavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
                    }
                    previousCharger = binding.charger
                }
            }

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

    }


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.setOnCameraIdleListener {
            loadChargepoints()
        }
        map.setOnMarkerClickListener { marker ->
            when (marker) {
                in markers -> {
                    binding.charger = markers[marker]
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
            binding.charger = null
        }

        if (hasLocationPermission()) {
            enableLocation(false)
        }
    }

    private fun enableLocation(animate: Boolean) {
        map.isMyLocationEnabled = true
        binding.myLocationEnabled = true
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

                chargepoints = response.body()!!.chargelocations
                updateMap()
            }
        })
    }

    private fun loadChargerDetails() {
        val id = binding.charger?.id ?: return
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

                binding.charger = response.body()!!.chargelocations[0] as ChargeLocation
            }
        })
    }

    private fun updateMap() {
        markers.keys.forEach { it.remove() }
        clusterMarkers.forEach { it.remove() }

        val iconGenerator = IconGenerator(this).apply {
            setBackground(getDrawable(R.drawable.marker_cluster_bg))
            setTextAppearance(R.style.TextAppearance_AppCompat_Inverse)
        }

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
}
