package net.vonforst.evmap.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.transition.TransitionManager
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.app.SharedElementCallback
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
import androidx.transition.TransitionInflater
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
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
import com.mahc.custombottomsheetbehavior.MergedAppBarLayoutBehavior
import io.michaelrocks.bimap.HashBiMap
import io.michaelrocks.bimap.MutableBiMap
import kotlinx.android.synthetic.main.fragment_map.*
import net.vonforst.evmap.*
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
import net.vonforst.evmap.viewmodel.*

const val REQUEST_AUTOCOMPLETE = 2
const val ARG_CHARGER_ID = "chargerId"
const val ARG_LAT = "lat"
const val ARG_LON = "lon"

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
    private var markers: MutableBiMap<Marker, ChargeLocation> = HashBiMap()
    private var clusterMarkers: List<Marker> = emptyList()
    private var searchResultMarker: Marker? = null

    private lateinit var clusterIconGenerator: ClusterIconGenerator
    private lateinit var chargerIconGenerator: ChargerIconGenerator
    private lateinit var animator: MarkerAnimator
    private lateinit var favToggle: MenuItem
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val value = vm.layersMenuOpen.value
            if (value != null && value) {
                closeLayersMenu()
                return
            }

            val state = bottomSheetBehavior.state
            if (state != STATE_COLLAPSED && state != STATE_HIDDEN) {
                bottomSheetBehavior.state = STATE_COLLAPSED
            } else if (state == STATE_COLLAPSED) {
                vm.chargerSparse.value = null
            } else if (state == STATE_HIDDEN) {
                vm.searchResult.value = null
            }
        }
    }

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

        binding.root.setOnApplyWindowInsetsListener { v, insets ->
            binding.detailAppBar.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.systemWindowInsetTop
            }
            insets
        }

        setExitSharedElementCallback(exitElementCallback)
        exitTransition = TransitionInflater.from(requireContext())
            .inflateTransition(R.transition.map_exit_transition)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )

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
        binding.fabLayers.setOnClickListener {
            openLayersMenu()
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
            val fields = listOf(Place.Field.LAT_LNG, Place.Field.VIEWPORT)
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
            bottomSheetBehavior.state = STATE_COLLAPSED
        }
        binding.detailAppBar.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_fav -> {
                    toggleFavorite()
                    true
                }
                R.id.menu_share -> {
                    val charger = vm.charger.value?.data
                    if (charger != null) {
                        (activity as? MapsActivity)?.shareUrl("https:${charger.url}")
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openLayersMenu() {
        val materialTransform = MaterialContainerTransform().apply {
            startView = binding.fabLayers
            endView = binding.layersSheet
            pathMotion = MaterialArcMotion()
            duration = 250
            scrimColor = Color.TRANSPARENT
        }
        TransitionManager.beginDelayedTransition(binding.root, materialTransform)
        vm.layersMenuOpen.value = true
    }

    private fun closeLayersMenu() {
        val materialTransform = MaterialContainerTransform().apply {
            startView = binding.layersSheet
            endView = binding.fabLayers
            pathMotion = MaterialArcMotion()
            duration = 200
            scrimColor = Color.TRANSPARENT
        }
        TransitionManager.beginDelayedTransition(binding.root, materialTransform)
        vm.layersMenuOpen.value = false
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
                updateBackPressedCallback()
            }
        })
        vm.chargerSparse.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                if (vm.bottomSheetState.value != BottomSheetBehaviorGoogleMapsLike.STATE_ANCHOR_POINT) {
                    bottomSheetBehavior.state = STATE_COLLAPSED
                }
                binding.fabDirections.show()
                detailAppBarBehavior.setToolbarTitle(it.name)
                updateFavoriteToggle()
                highlightMarker(it)
            } else {
                bottomSheetBehavior.state = STATE_HIDDEN
                unhighlightAllMarkers()
            }
        })
        vm.chargepoints.observe(viewLifecycleOwner, Observer {
            val chargepoints = it.data
            if (chargepoints != null) updateMap(chargepoints)
        })
        vm.favorites.observe(viewLifecycleOwner, Observer {
            updateFavoriteToggle()
        })
        vm.searchResult.observe(viewLifecycleOwner, Observer { place ->
            val map = this.map ?: return@Observer
            searchResultMarker?.remove()
            searchResultMarker = null

            if (place != null) {
                if (place.viewport != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(place.viewport, 0))
                } else {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(place.latLng, 12f))
                }

                searchResultMarker = map.addMarker(MarkerOptions().position(place.latLng!!))
            }

            updateBackPressedCallback()
        })
        vm.layersMenuOpen.observe(viewLifecycleOwner, Observer { open ->
            binding.fabLayers.visibility = if (open) View.GONE else View.VISIBLE
            binding.layersSheet.visibility = if (open) View.VISIBLE else View.GONE
            updateBackPressedCallback()
        })
        vm.mapType.observe(viewLifecycleOwner, Observer {
            map?.mapType = it
        })
        vm.mapTrafficEnabled.observe(viewLifecycleOwner, Observer {
            map?.isTrafficEnabled = it
        })
    }

    private fun updateBackPressedCallback() {
        backPressedCallback.isEnabled =
            vm.bottomSheetState.value != STATE_HIDDEN || vm.searchResult.value != null
                    || (vm.layersMenuOpen.value ?: false)
    }

    private fun unhighlightAllMarkers() {
        markers.forEach { (m, c) ->
            m.setIcon(
                chargerIconGenerator.getBitmapDescriptor(
                    getMarkerTint(c)
                )
            )
        }
    }

    private fun highlightMarker(charger: ChargeLocation) {
        val marker = markers.inverse[charger] ?: return
        // highlight this marker
        marker.setIcon(
            chargerIconGenerator.getBitmapDescriptor(
                getMarkerTint(charger), highlight = true
            )
        )
        animator.animateMarkerBounce(marker)

        // un-highlight all other markers
        markers.forEach { (m, c) ->
            if (m != marker) {
                m.setIcon(
                    chargerIconGenerator.getBitmapDescriptor(
                        getMarkerTint(c)
                    )
                )
            }
        }
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
            // make sure that the app does not freeze waiting for a picture to load
            Handler().postDelayed({
                startPostponedEnterTransition()
            }, 500)
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
        map.uiSettings.isTiltGesturesEnabled = false
        map.isIndoorEnabled = false
        map.uiSettings.isIndoorLevelPickerEnabled = false
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
            if (backPressedCallback.isEnabled) {
                backPressedCallback.handleOnBackPressed()
            }
        }

        // set padding so that compass is not obstructed by toolbar
        map.setPadding(0, binding.toolbarContainer.height, 0, 0)

        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        map.setMapStyle(
            if (mode == Configuration.UI_MODE_NIGHT_YES) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.maps_night_mode)
            } else null
        )


        val position = vm.mapPosition.value
        val lat = arguments?.optDouble(ARG_LAT)
        val lon = arguments?.optDouble(ARG_LON)
        var positionSet = false

        if (position != null) {
            val cameraUpdate =
                CameraUpdateFactory.newLatLngZoom(position.bounds.center, position.zoom)
            map.moveCamera(cameraUpdate)
            positionSet = true
        } else if (lat != null && lon != null) {
            // show given position
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f)
            map.moveCamera(cameraUpdate)

            // show charger detail after chargers were loaded
            val chargerId = arguments?.optLong(ARG_CHARGER_ID)
            vm.chargepoints.observe(
                viewLifecycleOwner,
                object : Observer<Resource<List<ChargepointListItem>>> {
                    override fun onChanged(res: Resource<List<ChargepointListItem>>) {
                        if (res.data == null) return
                        for (item in res.data) {
                            if (item is ChargeLocation && item.id == chargerId) {
                                vm.chargerSparse.value = item
                                vm.chargepoints.removeObserver(this)
                            }
                        }
                    }
                })

            positionSet = true
        }
        if (hasLocationPermission()) {
            enableLocation(!positionSet, false)
            positionSet = true
        }
        if (!positionSet) {
            // center the camera on Europe
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(LatLng(50.113388, 9.252536), 3.5f)
            map.moveCamera(cameraUpdate)
        }

        vm.mapPosition.value = MapPosition(
            map.projection.visibleRegion.latLngBounds, map.cameraPosition.zoom
        )
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
        // remove markers that disappeared
        markers.entries.toList().forEach {
            if (!chargepointIds.contains(it.value.id)) {
                if (it.key.isVisible) {
                    val tint = getMarkerTint(it.value)
                    val highlight = it.value == vm.chargerSparse.value
                    animator.animateMarkerDisappear(it.key, tint, highlight)
                } else {
                    it.key.remove()
                }
                markers.remove(it.key)
            }
        }
        // add new markers
        chargers.filter {
            !markers.containsValue(it)
        }.forEach { charger ->
            val tint = getMarkerTint(charger)
            val highlight = charger == vm.chargerSparse.value
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(charger.coordinates.lat, charger.coordinates.lng))
                    .icon(
                        chargerIconGenerator.getBitmapDescriptor(tint, highlight = highlight)
                    )
            )
            animator.animateMarkerAppear(marker, tint, highlight)
            markers[marker] = charger
        }
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

        val filterItem = menu.findItem(R.id.menu_filter)
        val filterView = filterItem.actionView

        val filterBadge = filterView?.findViewById<TextView>(R.id.filter_badge)
        if (filterBadge != null) {
            // set up badge showing number of active filters
            vm.filtersCount.observe(viewLifecycleOwner, Observer {
                filterBadge.visibility = if (it > 0) View.VISIBLE else View.GONE
                filterBadge.text = it.toString()
            })
        }
        filterView?.setOnClickListener {
            onOptionsItemSelected(filterItem)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_filter -> {
                requireView().findNavController().navigate(
                    R.id.action_map_to_filterFragment
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_AUTOCOMPLETE -> {
                if (resultCode == Activity.RESULT_OK) {
                    vm.searchResult.value = Autocomplete.getPlaceFromIntent(data!!)
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun getRootView(): View {
        return root
    }

    private val exitElementCallback: SharedElementCallback = object : SharedElementCallback() {
        override fun onMapSharedElements(
            names: MutableList<String>,
            sharedElements: MutableMap<String, View>
        ) {
            // Locate the ViewHolder for the clicked position.
            val position = galleryVm.galleryPosition.value ?: return

            val vh = binding.gallery.findViewHolderForAdapterPosition(position)
            if (vh?.itemView == null) return

            // Map the first shared element name to the child ImageView.
            sharedElements[names[0]] = vh.itemView
        }
    }

    companion object {
        fun showCharger(charger: ChargeLocation): Bundle {
            return Bundle().apply {
                putLong(ARG_CHARGER_ID, charger.id)
                putDouble(ARG_LAT, charger.coordinates.lat)
                putDouble(ARG_LON, charger.coordinates.lng)
            }
        }
    }
}