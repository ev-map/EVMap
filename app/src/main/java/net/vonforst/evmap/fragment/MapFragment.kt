package net.vonforst.evmap.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.snackbar.Snackbar
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike
import com.mahc.custombottomsheetbehavior.MergedAppBarLayoutBehavior
import kotlinx.android.synthetic.main.fragment_map.*
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.REQUEST_LOCATION_PERMISSION
import net.vonforst.evmap.adapter.ConnectorAdapter
import net.vonforst.evmap.adapter.DetailAdapter
import net.vonforst.evmap.adapter.GalleryAdapter
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.ChargeLocationCluster
import net.vonforst.evmap.api.goingelectric.ChargepointListItem
import net.vonforst.evmap.databinding.FragmentMapBinding
import net.vonforst.evmap.ui.ChargerIconGenerator
import net.vonforst.evmap.ui.ClusterIconGenerator
import net.vonforst.evmap.ui.MarkerAnimator
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.viewmodel.GalleryViewModel
import net.vonforst.evmap.viewmodel.MapPosition
import net.vonforst.evmap.viewmodel.MapViewModel
import net.vonforst.evmap.viewmodel.viewModelFactory

const val REQUEST_AUTOCOMPLETE = 2

class MapFragment : Fragment(), OnMapReadyCallback, MapsActivity.FragmentCallback {
    private lateinit var binding: FragmentMapBinding
    private val vm: MapViewModel by viewModels(factoryProducer = {
        viewModelFactory {
            MapViewModel(
                requireActivity().application,
                getString(R.string.goingelectric_key)
            )
        }
    })
    private val galleryVm: GalleryViewModel by activityViewModels()
    private var map: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var bottomSheetBehavior: BottomSheetBehaviorGoogleMapsLike<View>
    private lateinit var detailAppBarBehavior: MergedAppBarLayoutBehavior
    private var markers: Map<Marker, ChargeLocation> = emptyMap()
    private var clusterMarkers: List<Marker> = emptyList()

    private lateinit var clusterIconGenerator: ClusterIconGenerator
    private lateinit var chargerIconGenerator: ChargerIconGenerator
    private lateinit var animator: MarkerAnimator
    private lateinit var favToggle: MenuItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_map, container, false)
        binding.lifecycleOwner = this
        binding.vm = vm

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        clusterIconGenerator = ClusterIconGenerator(requireContext())
        chargerIconGenerator = ChargerIconGenerator(requireContext())
        animator = MarkerAnimator(chargerIconGenerator)

        setHasOptionsMenu(true)
        postponeEnterTransition()

        binding.toolbarContainer.viewTreeObserver.addOnGlobalLayoutListener {

        }
        binding.root.setOnApplyWindowInsetsListener { v, insets ->
            binding.detailAppBar.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.systemWindowInsetTop
            }
            insets
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        bottomSheetBehavior = BottomSheetBehaviorGoogleMapsLike.from(binding.bottomSheet)
        detailAppBarBehavior = MergedAppBarLayoutBehavior.from(binding.detailAppBar)

        binding.detailAppBar.toolbar.inflateMenu(R.menu.detail)
        favToggle = binding.detailAppBar.toolbar.menu.findItem(R.id.menu_fav)

        setupObservers()
        setupClickListeners()
        setupAdapters()

        val navController = findNavController()
        (activity as? MapsActivity)?.setSupportActionBar(binding.toolbar)
        binding.toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )
    }

    override fun onResume() {
        super.onResume()
        val hostActivity = activity as? MapsActivity ?: return
        hostActivity.fragmentCallback = this
    }

    private fun setupClickListeners() {
        binding.fabLocate.setOnClickListener {
            if (!hasLocationPermission()) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
                )
            } else {
                enableLocation(true, true)
            }
        }
        binding.fabDirections.setOnClickListener {
            val charger = vm.charger.value?.data
            if (charger != null) {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    (requireActivity() as MapsActivity).navigateTo(charger)
                }
            }
        }
        binding.detailView.goingelectricButton.setOnClickListener {
            val charger = vm.charger.value?.data
            if (charger != null) {
                (activity as? MapsActivity)?.openUrl("https:${charger.url}")
            }
        }
        binding.detailView.topPart.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_ANCHOR_POINT
        }
        binding.search.setOnClickListener {
            val fields = listOf(Place.Field.LAT_LNG)
            val intent: Intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY, fields
            )
                .build(requireContext())
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivityForResult(intent, REQUEST_AUTOCOMPLETE)

            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(0, 0)
        }
        binding.detailAppBar.toolbar.setNavigationOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
        }
        binding.detailAppBar.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_fav -> {
                    toggleFavorite()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleFavorite() {
        val favs = vm.favorites.value ?: return
        val charger = vm.chargerSparse.value ?: return
        if (favs.find { it.id == charger.id } != null) {
            vm.deleteFavorite(charger)
        } else {
            vm.insertFavorite(charger)
        }
    }

    private fun setupObservers() {
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehaviorGoogleMapsLike.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                vm.bottomSheetState.value = newState
            }
        })
        vm.chargerSparse.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                if (vm.bottomSheetState.value != BottomSheetBehaviorGoogleMapsLike.STATE_ANCHOR_POINT) {
                    bottomSheetBehavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
                }
                binding.fabDirections.show()
                detailAppBarBehavior.setToolbarTitle(it.name)
                updateFavoriteToggle()
            } else {
                bottomSheetBehavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
            }
        })
        vm.chargepoints.observe(viewLifecycleOwner, Observer {
            val chargepoints = it.data
            if (chargepoints != null) updateMap(chargepoints)
        })
        vm.favorites.observe(viewLifecycleOwner, Observer {
            updateFavoriteToggle()
        })
    }

    private fun updateFavoriteToggle() {
        val favs = vm.favorites.value ?: return
        val charger = vm.chargerSparse.value ?: return
        if (favs.find { it.id == charger.id } != null) {
            favToggle.setIcon(R.drawable.ic_fav)
        } else {
            favToggle.setIcon(R.drawable.ic_fav_no)
        }
    }

    private fun setupAdapters() {
        val galleryClickListener = object : GalleryAdapter.ItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                val photos = vm.charger.value?.data?.photos ?: return
                val extras = FragmentNavigatorExtras(view to view.transitionName)
                view.findNavController().navigate(
                    R.id.action_map_to_galleryFragment,
                    GalleryFragment.buildArgs(photos, position),
                    null,
                    extras
                )
            }
        }

        val galleryPosition = galleryVm.galleryPosition.value
        binding.gallery.apply {
            adapter = GalleryAdapter(context, galleryClickListener, pageToLoad = galleryPosition) {
                startPostponedEnterTransition()
            }
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context, LinearLayoutManager.HORIZONTAL
                ).apply {
                    setDrawable(context.getDrawable(R.drawable.gallery_divider)!!)
                })
        }
        if (galleryPosition == null) {
            startPostponedEnterTransition()
        } else {
            binding.gallery.scrollToPosition(galleryPosition)
        }

        binding.detailView.connectors.apply {
            adapter = ConnectorAdapter()
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.detailView.details.apply {
            adapter = DetailAdapter()
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context,
                    LinearLayoutManager.VERTICAL
                )
            )
        }
    }

    override fun onMapReady(map: GoogleMap) {
        this.map = map
        map.uiSettings.isTiltGesturesEnabled = false;
        map.setOnCameraIdleListener {
            vm.mapPosition.value = MapPosition(
                map.projection.visibleRegion.latLngBounds, map.cameraPosition.zoom
            )
        }
        map.setOnMarkerClickListener { marker ->
            when (marker) {
                in markers -> {
                    vm.chargerSparse.value = markers[marker]
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
            vm.chargerSparse.value = null
        }

        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        map.setMapStyle(
            if (mode == Configuration.UI_MODE_NIGHT_YES) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.maps_night_mode)
            } else null
        )


        val position = vm.mapPosition.value
        if (hasLocationPermission()) {
            enableLocation(position == null, false)
        } else if (position == null) {
            // center the camera on Europe
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(LatLng(50.113388, 9.252536), 3.5f)
            map.moveCamera(cameraUpdate)
        }

        if (position != null) {
            val cameraUpdate =
                CameraUpdateFactory.newLatLngZoom(position.bounds.center, position.zoom)
            map.moveCamera(cameraUpdate)
        } else {
            vm.mapPosition.value = MapPosition(
                map.projection.visibleRegion.latLngBounds, map.cameraPosition.zoom
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation(moveTo: Boolean, animate: Boolean) {
        val map = this.map ?: return
        map.isMyLocationEnabled = true
        vm.myLocationEnabled.value = true
        map.uiSettings.isMyLocationButtonEnabled = false
        if (moveTo) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    val camUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 13f)
                    if (animate) {
                        map.animateCamera(camUpdate)
                    } else {
                        map.moveCamera(camUpdate)
                    }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val context = context ?: return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateMap(chargepoints: List<ChargepointListItem>) {
        val map = this.map ?: return
        clusterMarkers.forEach { it.remove() }

        val clusters = chargepoints.filterIsInstance<ChargeLocationCluster>()
        val chargers = chargepoints.filterIsInstance<ChargeLocation>()

        val chargepointIds = chargers.map { it.id }.toSet()
        markers = markers.filter {
            if (!chargepointIds.contains(it.value.id)) {
                val tint = getMarkerTint(it.value)
                if (it.key.isVisible) {
                    animator.animateMarkerDisappear(it.key, tint)
                } else {
                    it.key.remove()
                }
                false
            } else {
                true
            }
        }
        markers = markers + chargers.filter {
            !markers.containsValue(it)
        }.map { charger ->
            val tint = getMarkerTint(charger)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(charger.coordinates.lat, charger.coordinates.lng))
                    .icon(
                        chargerIconGenerator.getBitmapDescriptor(tint)
                    )
            )
            animator.animateMarkerAppear(marker, tint)

            marker to charger
        }.toMap()
        clusterMarkers = clusters.map { cluster ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(cluster.coordinates.lat, cluster.coordinates.lng))
                    .icon(BitmapDescriptorFactory.fromBitmap(clusterIconGenerator.makeIcon(cluster.clusterCount.toString())))
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
                    enableLocation(true, true)
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_filter -> {
                Snackbar.make(root, R.string.not_implemented, Snackbar.LENGTH_SHORT).show()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_AUTOCOMPLETE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    val zoom = 12f
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(place.latLng, zoom))
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun goBack(): Boolean {
        return if (bottomSheetBehavior.state != BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED &&
            bottomSheetBehavior.state != BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
        ) {
            bottomSheetBehavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
            true
        } else if (bottomSheetBehavior.state == BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED) {
            vm.chargerSparse.value = null
            true
        } else {
            false
        }
    }

    override fun getRootView(): View {
        return root
    }
}