package com.johan.evmap

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.ui.IconGenerator
import com.johan.evmap.api.*
import com.johan.evmap.databinding.ActivityMapsBinding
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapsBinding
    private lateinit var map: GoogleMap
    private lateinit var api: GoingElectricApi
    private var chargepoints: List<ChargepointListItem> = emptyList()
    private var markers: Map<Marker, ChargeLocation> = emptyMap()
    private var clusterMarkers: List<Marker> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_maps)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        api = GoingElectricApi.create(getString(R.string.goingelectric_key))

        val behavior = BottomSheetBehaviorGoogleMapsLike.from(binding.bottomSheet);
        binding.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                when (propertyId) {
                    BR.charger -> {
                        if (binding.charger != null) {
                            behavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
                        } else {
                            behavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
                        }
                    }
                }
            }

        })
    }


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Add a marker in Sydney and move the camera
        val sydney = LatLng(54.0, 9.0)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney, 11f))

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
    }

    private fun loadChargepoints() {
        val bounds = map.projection.visibleRegion.latLngBounds
        api.getChargepoints(
            bounds.southwest.latitude, bounds.southwest.longitude,
            bounds.northeast.latitude, bounds.northeast.longitude,
            clustering = true, zoom = map.cameraPosition.zoom
        ).enqueue(object : Callback<ChargepointList> {
            override fun onFailure(call: Call<ChargepointList>, t: Throwable) {
                t.printStackTrace()
            }

            override fun onResponse(
                call: Call<ChargepointList>,
                response: Response<ChargepointList>
            ) {
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    return
                }

                chargepoints = response.body()!!.chargelocations
                updateMap()
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
                        BitmapDescriptorFactory.defaultMarker(
                            when {
                                charger.maxPower >= 100 -> BitmapDescriptorFactory.HUE_YELLOW
                                charger.maxPower >= 43 -> BitmapDescriptorFactory.HUE_ORANGE
                                charger.maxPower >= 20 -> BitmapDescriptorFactory.HUE_AZURE
                                charger.maxPower >= 11 -> BitmapDescriptorFactory.HUE_BLUE
                                else -> BitmapDescriptorFactory.HUE_GREEN
                            }
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
}
