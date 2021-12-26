package net.vonforst.evmap.fragment

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.text.method.KeyListener
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionInflater
import androidx.transition.TransitionManager
import coil.load
import coil.memory.MemoryCache
import coil.size.OriginalSize
import coil.size.SizeResolver
import com.car2go.maps.AnyMap
import com.car2go.maps.MapFragment
import com.car2go.maps.OnMapReadyCallback
import com.car2go.maps.model.BitmapDescriptor
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.Marker
import com.car2go.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
import com.mahc.custombottomsheetbehavior.MergedAppBarLayoutBehavior
import com.mapzen.android.lost.api.LocationListener
import com.mapzen.android.lost.api.LocationRequest
import com.mapzen.android.lost.api.LocationServices
import com.mapzen.android.lost.api.LostApiClient
import com.stfalcon.imageviewer.StfalconImageViewer
import io.michaelrocks.bimap.HashBiMap
import io.michaelrocks.bimap.MutableBiMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.vonforst.evmap.*
import net.vonforst.evmap.adapter.ConnectorAdapter
import net.vonforst.evmap.adapter.DetailsAdapter
import net.vonforst.evmap.adapter.GalleryAdapter
import net.vonforst.evmap.adapter.PlaceAutocompleteAdapter
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.autocomplete.ApiUnavailableException
import net.vonforst.evmap.autocomplete.PlaceWithBounds
import net.vonforst.evmap.databinding.FragmentMapBinding
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.ChargerIconGenerator
import net.vonforst.evmap.ui.ClusterIconGenerator
import net.vonforst.evmap.ui.MarkerAnimator
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.utils.boundingBox
import net.vonforst.evmap.utils.checkAnyLocationPermission
import net.vonforst.evmap.utils.checkFineLocationPermission
import net.vonforst.evmap.utils.distanceBetween
import net.vonforst.evmap.viewmodel.*
import java.io.IOException


class MapFragment : Fragment(), OnMapReadyCallback, MapsActivity.FragmentCallback,
    LostApiClient.ConnectionCallbacks, LocationListener {
    private lateinit var binding: FragmentMapBinding
    private val vm: MapViewModel by viewModels()
    private val galleryVm: GalleryViewModel by activityViewModels()
    private var mapFragment: MapFragment? = null
    private var map: AnyMap? = null
    private lateinit var locationClient: LostApiClient
    private var requestingLocationUpdates = false
    private lateinit var bottomSheetBehavior: BottomSheetBehaviorGoogleMapsLike<View>
    private lateinit var detailAppBarBehavior: MergedAppBarLayoutBehavior
    private lateinit var prefs: PreferenceDataSource
    private var markers: MutableBiMap<Marker, ChargeLocation> = HashBiMap()
    private var clusterMarkers: List<Marker> = emptyList()
    private var searchResultMarker: Marker? = null
    private var searchResultIcon: BitmapDescriptor? = null
    private var connectionErrorSnackbar: Snackbar? = null
    private var previousChargepointIds: Set<Long>? = null

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

            if (binding.search.hasFocus()) {
                removeSearchFocus()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceDataSource(requireContext())

        locationClient = LostApiClient.Builder(requireContext())
            .addConnectionCallbacks(this)
            .build()
        locationClient.connect()
        clusterIconGenerator = ClusterIconGenerator(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_map, container, false)
        binding.lifecycleOwner = this
        binding.vm = vm

        val provider = prefs.mapProvider
        if (mapFragment == null || mapFragment!!.priority[0] != provider) {
            mapFragment = MapFragment()
            mapFragment!!.priority = arrayOf(
                when (provider) {
                    "mapbox" -> MapFragment.MAPBOX
                    "google" -> MapFragment.GOOGLE
                    else -> null
                },
                MapFragment.GOOGLE,
                MapFragment.MAPBOX
            )
            requireActivity().supportFragmentManager
                .beginTransaction()
                .replace(R.id.map, mapFragment!!)
                .commit()

            // reset map-related stuff (map provider may have changed)
            map = null
            markers.clear()
            clusterMarkers = emptyList()
            searchResultMarker = null
            searchResultIcon = null
        }

        setHasOptionsMenu(true)

        binding.root.setOnApplyWindowInsetsListener { _, insets ->
            binding.detailAppBar.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.systemWindowInsetTop
            }

            // margin of layers button
            val density = resources.displayMetrics.density
            // status bar height + toolbar height + margin
            val margin =
                if (binding.toolbarContainer.layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                    insets.systemWindowInsetTop + (48 * density).toInt() + (28 * density).toInt()
                } else {
                    insets.systemWindowInsetTop + (12 * density).toInt()
                }
            binding.fabLayers.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = margin
            }
            binding.layersSheet.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = margin
            }
            insets
        }

        exitTransition = TransitionInflater.from(requireContext())
            .inflateTransition(R.transition.map_exit_transition)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mapFragment!!.getMapAsync(this)
        bottomSheetBehavior = BottomSheetBehaviorGoogleMapsLike.from(binding.bottomSheet)
        detailAppBarBehavior = MergedAppBarLayoutBehavior.from(binding.detailAppBar)

        binding.detailAppBar.toolbar.inflateMenu(R.menu.detail)
        favToggle = binding.detailAppBar.toolbar.menu.findItem(R.id.menu_fav)
        binding.detailAppBar.toolbar.menu.findItem(R.id.menu_edit).title =
            getString(R.string.edit_at_datasource, vm.apiName)

        binding.detailView.topPart.doOnNextLayout {
            bottomSheetBehavior.peekHeight = binding.detailView.topPart.bottom
        }

        setupObservers()
        setupClickListeners()
        setupAdapters()
        (activity as? MapsActivity)?.setSupportActionBar(binding.toolbar)

        if (prefs.appStartCounter > 5 && !prefs.opensourceDonationsDialogShown) {
            try {
                findNavController().navigate(R.id.action_map_to_opensource_donations)
            } catch (ignored: IllegalArgumentException) {
                // when there is already another navigation going on
            } catch (ignored: IllegalStateException) {
                // "no current navigation node"
            }
        }
        /*if (!prefs.update060AndroidAutoDialogShown) {
            try {
                navController.navigate(R.id.action_map_to_update_060_androidauto)
            } catch (ignored: IllegalArgumentException) {
                // when there is already another navigation going on
            }
        }*/

        val fragmentArgs: MapFragmentArgs by navArgs()
        if (savedInstanceState == null && fragmentArgs.appStart) {
            // logo animation after starting the app
            binding.appLogo.root.visibility = View.VISIBLE
            binding.appLogo.root.alpha = 0f
            binding.search.visibility = View.GONE

            binding.appLogo.root.animate().alpha(1f)
                .withEndAction {
                    binding.appLogo.root.animate().alpha(0f).apply {
                        startDelay = 1000
                    }.withEndAction {
                        binding.appLogo.root.visibility = View.GONE
                        binding.search.visibility = View.VISIBLE
                        binding.search.alpha = 0f
                        binding.search.animate().alpha(1f).start()
                    }.start()
                }.apply {
                    startDelay = 100
                }.start()
            arguments = fragmentArgs.copy(appStart = false).toBundle()
        } else {
            binding.appLogo.root.visibility = View.GONE
            binding.search.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        val hostActivity = activity as? MapsActivity ?: return
        hostActivity.fragmentCallback = this

        val navController = findNavController()
        binding.toolbar.setupWithNavController(
            navController,
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        vm.reloadPrefs()
        if (requestingLocationUpdates && requireContext().checkAnyLocationPermission()
            && locationClient.isConnected
        ) {
            requestLocationUpdates()
        }
    }

    private fun setupClickListeners() {
        binding.fabLocate.setOnClickListener {
            if (!requireContext().checkFineLocationPermission()) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
                )
            }
            if (requireContext().checkAnyLocationPermission()) {
                enableLocation(moveTo = true, animate = true)
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
        binding.layers.btnClose.setOnClickListener {
            closeLayersMenu()
        }
        binding.detailView.sourceButton.setOnClickListener {
            val charger = vm.charger.value?.data
            if (charger != null) {
                (activity as? MapsActivity)?.openUrl(charger.url)
            }
        }
        binding.detailView.btnChargeprice.setOnClickListener {
            val charger = vm.charger.value?.data ?: return@setOnClickListener
            val dataSource = when (vm.apiType) {
                GoingElectricApiWrapper::class.java -> "going_electric"
                OpenChargeMapApiWrapper::class.java -> "open_charge_map"
                else -> throw IllegalArgumentException("unsupported data source")
            }
            findNavController().navigate(
                R.id.action_map_to_chargepriceFragment,
                ChargepriceFragmentArgs(charger, dataSource).toBundle()
            )
        }
        binding.detailView.topPart.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehaviorGoogleMapsLike.STATE_ANCHOR_POINT
        }
        setupSearchAutocomplete()
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
                        (activity as? MapsActivity)?.shareUrl(charger.url)
                    }
                    true
                }
                R.id.menu_edit -> {
                    val charger = vm.charger.value?.data
                    if (charger?.editUrl != null) {
                        (activity as? MapsActivity)?.openUrl(charger.editUrl)
                        if (vm.apiType == GoingElectricApiWrapper::class.java) {
                            // instructions specific to GoingElectric
                            Toast.makeText(
                                requireContext(),
                                R.string.edit_on_goingelectric_info,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    var searchKeyListener: KeyListener? = null

    @SuppressLint("SetTextI18n")
    private fun setupSearchAutocomplete() {
        binding.search.threshold = 1

        searchKeyListener = binding.search.keyListener
        binding.search.keyListener = null

        val adapter = PlaceAutocompleteAdapter(requireContext(), vm.location)
        binding.search.setAdapter(adapter)
        binding.search.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val place = adapter.getItem(position) ?: return@OnItemClickListener
                lifecycleScope.launch {
                    try {
                        vm.searchResult.value = adapter.getDetails(place.id)
                    } catch (e: ApiUnavailableException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        // TODO: show error
                        e.printStackTrace()
                    }
                }
                removeSearchFocus()
                binding.search.setText(
                    if (place.secondaryText.isNotEmpty()) {
                        "${place.primaryText}, ${place.secondaryText}"
                    } else {
                        place.primaryText.toString()
                    }
                )
            }
        binding.search.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                binding.search.keyListener = searchKeyListener
            } else {
                binding.search.keyListener = null
            }
            updateBackPressedCallback()
        }
        binding.clearSearch.setOnClickListener {
            vm.searchResult.value = null
            removeSearchFocus()
        }
        binding.toolbar.doOnLayout {
            binding.search.dropDownWidth = binding.toolbar.width
            binding.search.dropDownAnchor = R.id.toolbar
        }
    }

    private fun removeSearchFocus() {
        // clear focus and hide keyboard
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.search.windowToken, 0)
        binding.search.clearFocus()
    }

    private fun openLayersMenu() {
        binding.fabLayers.tag = false
        val materialTransform = MaterialContainerTransform().apply {
            startView = binding.fabLayers
            endView = binding.layersSheet
            setPathMotion(MaterialArcMotion())
            duration = 250
            scrimColor = Color.TRANSPARENT
        }
        TransitionManager.beginDelayedTransition(binding.root, materialTransform)
        vm.layersMenuOpen.value = true
    }

    private fun closeLayersMenu() {
        binding.fabLayers.tag = true
        val materialTransform = MaterialContainerTransform().apply {
            startView = binding.layersSheet
            endView = binding.fabLayers
            setPathMotion(MaterialArcMotion())
            duration = 200
            scrimColor = Color.TRANSPARENT
        }
        TransitionManager.beginDelayedTransition(binding.root, materialTransform)
        vm.layersMenuOpen.value = false
    }

    private fun toggleFavorite() {
        val favs = vm.favorites.value ?: return
        val charger = vm.chargerSparse.value ?: return
        val isFav = favs.find { it.id == charger.id } != null
        if (isFav) {
            vm.deleteFavorite(charger)
        } else {
            vm.insertFavorite(charger)
        }
        markers.inverse[charger]?.setIcon(
            chargerIconGenerator.getBitmapDescriptor(
                getMarkerTint(charger, vm.filteredConnectors.value),
                highlight = true,
                fault = charger.faultReport != null,
                multi = charger.isMulti(vm.filteredConnectors.value),
                fav = !isFav
            )
        )
    }

    private fun setupObservers() {
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehaviorGoogleMapsLike.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                vm.bottomSheetState.value = newState
                updateBackPressedCallback()

                if (vm.layersMenuOpen.value!! && newState !in listOf(
                        BottomSheetBehaviorGoogleMapsLike.STATE_SETTLING,
                        BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN,
                        BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
                    )
                ) {
                    closeLayersMenu()
                }
            }
        })
        vm.chargerSparse.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                if (vm.bottomSheetState.value != BottomSheetBehaviorGoogleMapsLike.STATE_ANCHOR_POINT) {
                    bottomSheetBehavior.state = STATE_COLLAPSED
                }
                removeSearchFocus()
                binding.fabDirections.show()
                detailAppBarBehavior.setToolbarTitle(it.name)
                updateFavoriteToggle()
                highlightMarker(it)
            } else {
                bottomSheetBehavior.state = STATE_HIDDEN
                unhighlightAllMarkers()
            }
        })
        vm.chargepoints.observe(viewLifecycleOwner, Observer { res ->
            when (res.status) {
                Status.ERROR -> {
                    val view = view ?: return@Observer

                    connectionErrorSnackbar?.dismiss()
                    connectionErrorSnackbar = Snackbar
                        .make(view, R.string.connection_error, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry) {
                            connectionErrorSnackbar?.dismiss()
                            vm.reloadChargepoints()
                        }
                    connectionErrorSnackbar!!.show()
                }
                Status.SUCCESS -> {
                    connectionErrorSnackbar?.dismiss()
                }
                Status.LOADING -> {
                }
            }

            val chargepoints = res.data
            if (chargepoints != null) {
                updateMap(chargepoints)
            }
        })
        vm.favorites.observe(viewLifecycleOwner, Observer {
            updateFavoriteToggle()
        })
        vm.searchResult.observe(viewLifecycleOwner, Observer { place ->
            val map = this.map ?: return@Observer
            searchResultMarker?.remove()
            searchResultMarker = null

            if (place != null) {
                // disable location following when search result is shown
                vm.myLocationEnabled.value = false
                if (place.viewport != null) {
                    map.animateCamera(map.cameraUpdateFactory.newLatLngBounds(place.viewport, 0))
                } else {
                    map.animateCamera(map.cameraUpdateFactory.newLatLngZoom(place.latLng, 12f))
                }

                if (searchResultIcon == null) {
                    searchResultIcon =
                        map.bitmapDescriptorFactory.fromResource(R.drawable.ic_map_marker)
                }
                searchResultMarker = map.addMarker(
                    MarkerOptions()
                        .position(place.latLng)
                        .icon(searchResultIcon)
                        .anchor(0.5f, 1f)
                )
            } else {
                binding.search.setText("")
            }

            updateBackPressedCallback()
        })
        vm.layersMenuOpen.observe(viewLifecycleOwner, Observer { open ->
            binding.fabLayers.visibility = if (open) View.GONE else View.VISIBLE
            binding.layersSheet.visibility = if (open) View.VISIBLE else View.GONE
            updateBackPressedCallback()
        })
        vm.mapType.observe(viewLifecycleOwner, Observer {
            map?.setMapType(it)
        })
        vm.mapTrafficEnabled.observe(viewLifecycleOwner, Observer {
            map?.setTrafficEnabled(it)
        })

        updateBackPressedCallback()
    }

    private fun updateBackPressedCallback() {
        backPressedCallback.isEnabled =
            vm.bottomSheetState.value != null && vm.bottomSheetState.value != STATE_HIDDEN
                    || vm.searchResult.value != null
                    || (vm.layersMenuOpen.value ?: false)
                    || binding.search.hasFocus()
    }

    private fun unhighlightAllMarkers() {
        markers.forEach { (m, c) ->
            m.setIcon(
                chargerIconGenerator.getBitmapDescriptor(
                    getMarkerTint(c, vm.filteredConnectors.value),
                    highlight = false,
                    fault = c.faultReport != null,
                    multi = c.isMulti(vm.filteredConnectors.value),
                    fav = c.id in vm.favorites.value?.map { it.id } ?: emptyList()
                )
            )
        }
    }

    private fun highlightMarker(charger: ChargeLocation) {
        val marker = markers.inverse[charger] ?: return
        // highlight this marker
        marker.setIcon(
            chargerIconGenerator.getBitmapDescriptor(
                getMarkerTint(charger, vm.filteredConnectors.value),
                highlight = true,
                fault = charger.faultReport != null,
                multi = charger.isMulti(vm.filteredConnectors.value),
                fav = charger.id in vm.favorites.value?.map { it.id } ?: emptyList()
            )
        )
        animator.animateMarkerBounce(marker)

        // un-highlight all other markers
        markers.forEach { (m, c) ->
            if (m != marker) {
                m.setIcon(
                    chargerIconGenerator.getBitmapDescriptor(
                        getMarkerTint(c, vm.filteredConnectors.value),
                        highlight = false,
                        fault = c.faultReport != null,
                        multi = c.isMulti(vm.filteredConnectors.value),
                        fav = c.id in vm.favorites.value?.map { it.id } ?: emptyList()
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
        var viewer: StfalconImageViewer<ChargerPhoto>? = null
        val galleryClickListener = object : GalleryAdapter.ItemClickListener {
            override fun onItemClick(view: View, position: Int, imageCacheKey: MemoryCache.Key?) {
                val photos = vm.charger.value?.data?.photos ?: return

                viewer = StfalconImageViewer.Builder(context, photos) { imageView, photo ->
                    imageView.load(photo.getUrl(size = 1000)) {
                        if (photo == photos[position] && imageCacheKey != null) {
                            placeholderMemoryCacheKey(imageCacheKey)
                        }
                        size(SizeResolver(OriginalSize))
                        allowHardware(false)
                    }
                }
                    .withTransitionFrom(view as ImageView)
                    .withImageChangeListener {
                        binding.gallery.layoutManager!!.scrollToPosition(it)
                        binding.gallery.layoutManager!!.findViewByPosition(it)?.let {
                            viewer?.updateTransitionImage(it as ImageView)
                        }
                    }
                    .withStartPosition(position)
                    .show()

            }
        }

        binding.gallery.apply {
            adapter = GalleryAdapter(context, galleryClickListener)
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(
                DividerItemDecoration(
                    context, LinearLayoutManager.HORIZONTAL
                ).apply {
                    setDrawable(ContextCompat.getDrawable(context, R.drawable.gallery_divider)!!)
                })
        }

        binding.detailView.connectors.apply {
            adapter = ConnectorAdapter()
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.detailView.details.apply {
            adapter = DetailsAdapter().apply {
                onClickListener = {
                    val charger = vm.chargerDetails.value?.data
                    if (charger != null) {
                        when (it.icon) {
                            R.drawable.ic_location, R.drawable.ic_address -> {
                                (activity as? MapsActivity)?.showLocation(charger)
                            }
                            R.drawable.ic_fault_report -> {
                                (activity as? MapsActivity)?.openUrl(charger.url)
                            }
                            R.drawable.ic_payment -> {
                                showPaymentMethodsDialog(charger)
                            }
                        }
                    }
                }
            }
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

    private fun showPaymentMethodsDialog(charger: ChargeLocation) {
        val activity = activity ?: return
        val chargecardData = vm.chargeCardMap.value ?: return
        val chargecards = charger.chargecards ?: return
        val filteredChargeCards = vm.filteredChargeCards.value

        val data = chargecards.mapNotNull { chargecardData[it.id] }
            .sortedBy { it.name }
            .sortedByDescending { filteredChargeCards?.contains(it.id) }
        val names = data.map {
            if (filteredChargeCards?.contains(it.id) == true) {
                it.name.bold()
            } else {
                it.name
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.charge_cards)
            .setItems(names.toTypedArray()) { _, i ->
                val card = data[i]
                (activity as? MapsActivity)?.openUrl("https:${card.url}")
            }.show()
    }

    override fun onMapReady(map: AnyMap) {
        this.map = map
        chargerIconGenerator = ChargerIconGenerator(requireContext(), map.bitmapDescriptorFactory)

        if (BuildConfig.FLAVOR == "google" && mapFragment!!.priority[0] == MapFragment.GOOGLE) {
            // Google Maps: icons can be generated in background thread
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    chargerIconGenerator.preloadCache()
                }
            }
        } else {
            // Mapbox: needs to be run on main thread
            chargerIconGenerator.preloadCache()
        }



        animator = MarkerAnimator(chargerIconGenerator)
        map.uiSettings.setTiltGesturesEnabled(false)
        map.setIndoorEnabled(false)
        map.uiSettings.setIndoorLevelPickerEnabled(false)
        map.setOnCameraIdleListener {
            vm.mapPosition.value = MapPosition(
                map.projection.visibleRegion.latLngBounds, map.cameraPosition.zoom
            )
            binding.scaleView.update(map.cameraPosition.zoom, map.cameraPosition.target.latitude)
        }
        map.setOnCameraMoveListener {
            binding.scaleView.update(map.cameraPosition.zoom, map.cameraPosition.target.latitude)
        }
        map.setOnCameraMoveStartedListener { reason ->
            if (reason == AnyMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                if (vm.myLocationEnabled.value == true) {
                    // disable location following when manually scrolling the map
                    vm.myLocationEnabled.value = false
                    removeLocationUpdates()
                }
                if (vm.layersMenuOpen.value == true) {
                    // close layers menu if open
                    closeLayersMenu()
                }
            }
        }
        map.setOnMarkerClickListener { marker ->
            when (marker) {
                in markers -> {
                    vm.chargerSparse.value = markers[marker]
                    true
                }
                in clusterMarkers -> {
                    val newZoom = map.cameraPosition.zoom + 2
                    map.animateCamera(
                        map.cameraUpdateFactory.newLatLngZoom(
                            marker.position,
                            newZoom
                        )
                    )
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
        map.setMapType(vm.mapType.value)
        map.setTrafficEnabled(vm.mapTrafficEnabled.value ?: false)

        // set padding so that compass is not obstructed by toolbar
        map.setPadding(0, binding.toolbarContainer.height, 0, 0)

        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        map.setMapStyle(
            if (mode == Configuration.UI_MODE_NIGHT_YES) AnyMap.Style.DARK else AnyMap.Style.NORMAL
        )


        val position = vm.mapPosition.value
        val fragmentArgs: MapFragmentArgs by navArgs()
        val locationName = fragmentArgs.locationName
        val chargerId = fragmentArgs.chargerId
        val latLng = fragmentArgs.latLng

        var positionSet = false

        if (position != null) {
            val cameraUpdate =
                map.cameraUpdateFactory.newLatLngZoom(position.bounds.center, position.zoom)
            map.moveCamera(cameraUpdate)
            positionSet = true
        } else if (chargerId != 0L && latLng == null) {
            // show given charger ID
            vm.loadChargerById(chargerId)
            vm.chargerSparse.observe(
                viewLifecycleOwner,
                object : Observer<ChargeLocation> {
                    override fun onChanged(item: ChargeLocation?) {
                        if (item?.id == chargerId) {
                            val cameraUpdate = map.cameraUpdateFactory.newLatLngZoom(
                                LatLng(item.coordinates.lat, item.coordinates.lng), 16f
                            )
                            map.moveCamera(cameraUpdate)
                            vm.chargerSparse.removeObserver(this)
                        }
                    }
                })

            positionSet = true
        } else if (latLng != null) {
            // show given position
            val cameraUpdate = map.cameraUpdateFactory.newLatLngZoom(latLng, 16f)
            map.moveCamera(cameraUpdate)

            if (chargerId != 0L) {
                // show charger detail after chargers were loaded
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
            } else {
                // mark location as search result
                vm.searchResult.value = PlaceWithBounds(latLng, boundingBox(latLng, 750.0))
            }

            positionSet = true
        } else if (locationName != null) {
            lifecycleScope.launch {
                val address = withContext(Dispatchers.IO) {
                    Geocoder(requireContext()).getFromLocationName(locationName, 1).getOrNull(0)
                }
                address?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    val cameraUpdate = map.cameraUpdateFactory.newLatLngZoom(latLng, 16f)
                    map.moveCamera(cameraUpdate)
                    val bboxSize = if (it.subAdminArea != null) {
                        750.0 // this is a place within a city
                    } else if (it.adminArea != null && it.adminArea != it.featureName) {
                        4000.0 // this is a city
                    } else if (it.adminArea != null) {
                        100000.0 // this is a top-level administrative area (i.e. state)
                    } else {
                        500000.0 // this is a country
                    }
                    vm.searchResult.value = PlaceWithBounds(latLng, boundingBox(latLng, bboxSize))
                }
            }
        }
        if (context?.checkAnyLocationPermission() ?: false) {
            enableLocation(!positionSet, false)
            positionSet = true
        }
        if (!positionSet) {
            // center the camera on Europe
            val cameraUpdate =
                map.cameraUpdateFactory.newLatLngZoom(LatLng(50.113388, 9.252536), 3.5f)
            map.moveCamera(cameraUpdate)
        }

        vm.mapPosition.value = MapPosition(
            map.projection.visibleRegion.latLngBounds, map.cameraPosition.zoom
        )

        if (vm.searchResult.value != null) {
            // show search result (after configuration change)
            vm.searchResult.postValue(vm.searchResult.value)
        }
    }

    @RequiresPermission(anyOf = [ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION])
    private fun enableLocation(moveTo: Boolean, animate: Boolean) {
        val map = this.map ?: return
        map.setMyLocationEnabled(true)
        map.uiSettings.setMyLocationButtonEnabled(false)
        if (moveTo) {
            vm.myLocationEnabled.value = true
            if (locationClient.isConnected) {
                moveToLastLocation(map, animate)
                requestLocationUpdates()
            }
        }
    }

    @RequiresPermission(anyOf = [ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION])
    private fun moveToLastLocation(map: AnyMap, animate: Boolean) {
        val location = LocationServices.FusedLocationApi.getLastLocation(locationClient)
        if (location != null) {
            val latLng = LatLng(location.latitude, location.longitude)
            vm.location.value = latLng
            val camUpdate = map.cameraUpdateFactory.newLatLngZoom(latLng, 13f)
            if (animate) {
                map.animateCamera(camUpdate)
            } else {
                map.moveCamera(camUpdate)
            }
        }
    }

    @Synchronized
    private fun updateMap(chargepoints: List<ChargepointListItem>) {
        val map = this.map ?: return
        clusterMarkers.forEach { it.remove() }

        val clusters = chargepoints.filterIsInstance<ChargeLocationCluster>()
        val chargers = chargepoints.filterIsInstance<ChargeLocation>()

        val chargepointIds = chargers.map { it.id }.toSet()

        // update icons of existing markers (connector filter may have changed)
        for ((marker, charger) in markers) {
            marker.setIcon(
                chargerIconGenerator.getBitmapDescriptor(
                    getMarkerTint(charger, vm.filteredConnectors.value),
                    highlight = charger == vm.chargerSparse.value,
                    fault = charger.faultReport != null,
                    multi = charger.isMulti(vm.filteredConnectors.value),
                    fav = charger.id in vm.favorites.value?.map { it.id } ?: emptyList()
                )
            )
        }

        if (chargers.toSet() != markers.values) {
            // remove markers that disappeared
            val bounds = map.projection.visibleRegion.latLngBounds
            markers.entries.toList().forEach {
                val marker = it.key
                val charger = it.value
                if (!chargepointIds.contains(charger.id)) {
                    // animate marker if it is visible, otherwise remove immediately
                    if (bounds.contains(marker.position)) {
                        val tint = getMarkerTint(charger, vm.filteredConnectors.value)
                        val highlight = charger == vm.chargerSparse.value
                        val fault = charger.faultReport != null
                        val multi = charger.isMulti(vm.filteredConnectors.value)
                        val fav = charger.id in vm.favorites.value?.map { it.id } ?: emptyList()
                        animator.animateMarkerDisappear(marker, tint, highlight, fault, multi, fav)
                    } else {
                        animator.deleteMarker(marker)
                    }
                    markers.remove(marker)
                }
            }
            // add new markers
            val map1 = markers.values.map { it.id }
            for (charger in chargers) {
                if (!map1.contains(charger.id)) {
                    val tint = getMarkerTint(charger, vm.filteredConnectors.value)
                    val highlight = charger == vm.chargerSparse.value
                    val fault = charger.faultReport != null
                    val multi = charger.isMulti(vm.filteredConnectors.value)
                    val fav = charger.id in vm.favorites.value?.map { it.id } ?: emptyList()
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(charger.coordinates.lat, charger.coordinates.lng))
                            .icon(
                                chargerIconGenerator.getBitmapDescriptor(
                                    tint,
                                    0f,
                                    255,
                                    highlight,
                                    fault,
                                    multi,
                                    fav
                                )
                            )
                            .anchor(0.5f, 1f)
                    )
                    animator.animateMarkerAppear(marker, tint, highlight, fault, multi, fav)
                    markers[marker] = charger
                }
            }
            previousChargepointIds = chargepointIds
        }
        clusterMarkers = clusters.map { cluster ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(cluster.coordinates.lat, cluster.coordinates.lng))
                    .icon(
                        map.bitmapDescriptorFactory.fromBitmap(
                            clusterIconGenerator.makeIcon(
                                cluster.clusterCount.toString()
                            )
                        )
                    )
                    .anchor(0.5f, 0.5f)
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED })) {
                    enableLocation(moveTo = true, animate = true)
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
            var profilesMap: MutableBiMap<Long, MenuItem> = HashBiMap()

            val popup = PopupMenu(
                ContextThemeWrapper(requireContext(), R.style.RoundedPopup),
                it,
                Gravity.END
            )
            popup.menuInflater.inflate(R.menu.popup_filter, popup.menu)
            MenuCompat.setGroupDividerEnabled(popup.menu, true)
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_edit_filters -> {
                        lifecycleScope.launch {
                            vm.copyFiltersToCustom()
                            requireView().findNavController().navigate(
                                R.id.action_map_to_filterFragment
                            )
                        }
                        true
                    }
                    R.id.menu_manage_filter_profiles -> {
                        requireView().findNavController().navigate(
                            R.id.action_map_to_filterProfilesFragment
                        )
                        true
                    }
                    else -> {
                        val profileId = profilesMap.inverse[it]
                        if (profileId != null) {
                            vm.filterStatus.value = profileId
                        }
                        true
                    }
                }
            }

            vm.filterProfiles.observe(viewLifecycleOwner, { profiles ->
                popup.menu.removeGroup(R.id.menu_group_filter_profiles)

                val noFiltersItem = popup.menu.add(
                    R.id.menu_group_filter_profiles,
                    Menu.NONE, Menu.NONE, R.string.no_filters
                )
                val favoritesItem = popup.menu.add(
                    R.id.menu_group_filter_profiles,
                    Menu.NONE,
                    Menu.NONE, R.string.filter_favorites
                )
                profiles.forEach { profile ->
                    val item = popup.menu.add(
                        R.id.menu_group_filter_profiles,
                        Menu.NONE,
                        Menu.NONE,
                        profile.name
                    )
                    profilesMap[profile.id] = item
                }
                val customItem = popup.menu.add(
                    R.id.menu_group_filter_profiles,
                    Menu.NONE, Menu.NONE, R.string.filter_custom
                )

                profilesMap[FILTERS_DISABLED] = noFiltersItem
                profilesMap[FILTERS_CUSTOM] = customItem
                profilesMap[FILTERS_FAVORITES] = favoritesItem

                popup.menu.setGroupCheckable(R.id.menu_group_filter_profiles, true, true);

                val manageFiltersItem = popup.menu.findItem(R.id.menu_manage_filter_profiles)
                manageFiltersItem.isVisible = profiles.isNotEmpty()

                vm.filterStatus.observe(viewLifecycleOwner, Observer { id ->
                    when (id) {
                        FILTERS_DISABLED -> {
                            customItem.isVisible = false
                            noFiltersItem.isChecked = true
                        }
                        FILTERS_CUSTOM -> {
                            customItem.isVisible = true
                            customItem.isChecked = true
                        }
                        FILTERS_FAVORITES -> {
                            customItem.isVisible = false
                            favoritesItem.isChecked = true
                        }
                        else -> {
                            customItem.isVisible = false
                            val item = profilesMap[id]
                            if (item != null) {
                                item.isChecked = true
                            }
                            // else unknown ID -> wait for filterProfiles to update
                        }
                    }
                })
            })
            popup.show()
        }

        filterView?.setOnLongClickListener {
            // enable/disable filters
            vm.toggleFilters()
            // haptic feedback
            filterView.performHapticFeedback(
                HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            // show snackbar
            Snackbar.make(
                requireView(), if (vm.filterStatus.value != FILTERS_DISABLED) {
                    R.string.filters_activated
                } else {
                    R.string.filters_deactivated
                }, Snackbar.LENGTH_SHORT
            ).show()
            true
        }
    }

    override fun getRootView(): View {
        return binding.root
    }

    override fun onConnected() {
        val map = this.map ?: return
        val context = this.context ?: return
        if (vm.myLocationEnabled.value == true) {
            if (context.checkAnyLocationPermission()) {
                moveToLastLocation(map, false)
                requestLocationUpdates()
            }
        }
    }

    @RequiresPermission(ACCESS_FINE_LOCATION)
    private fun requestLocationUpdates() {
        val request: LocationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(5000)
        LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, request, this)
        requestingLocationUpdates = true
    }

    private fun removeLocationUpdates() {
        if (locationClient.isConnected) {
            LocationServices.FusedLocationApi.removeLocationUpdates(locationClient, this)
        }
    }

    override fun onConnectionSuspended() {
    }

    override fun onLocationChanged(location: Location?) {
        val map = this.map ?: return
        if (location == null || vm.myLocationEnabled.value == false) return

        val latLng = LatLng(location.latitude, location.longitude)
        val oldLoc = vm.location.value
        if (latLng != oldLoc && (oldLoc == null || distanceBetween(
                latLng.latitude,
                latLng.longitude,
                oldLoc.latitude,
                oldLoc.longitude
            ) > 1)
        ) {
            // only update map if location changed by more than 1 meter
            vm.location.value = latLng
            val camUpdate = map.cameraUpdateFactory.newLatLng(latLng)
            map.animateCamera(camUpdate)
        }
    }

    override fun onPause() {
        super.onPause()
        removeLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (locationClient.isConnected) {
            locationClient.disconnect()
        }
    }
}