package net.vonforst.evmap.fragment

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.method.KeyListener
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionInflater
import androidx.transition.TransitionManager
import coil.load
import coil.memory.MemoryCache
import com.car2go.maps.AnyMap
import com.car2go.maps.MapFactory
import com.car2go.maps.MapFragment
import com.car2go.maps.OnMapReadyCallback
import com.car2go.maps.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialArcMotion
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_CROSS
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.BottomSheetCallback
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.STATE_ANCHOR_POINT
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.STATE_SETTLING
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike.from
import com.mahc.custombottomsheetbehavior.MergedAppBarLayoutBehavior
import com.stfalcon.imageviewer.StfalconImageViewer
import io.michaelrocks.bimap.HashBiMap
import io.michaelrocks.bimap.MutableBiMap
import kotlinx.coroutines.launch
import net.vonforst.evmap.MapsActivity
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.ConnectorAdapter
import net.vonforst.evmap.adapter.DetailsAdapter
import net.vonforst.evmap.adapter.GalleryAdapter
import net.vonforst.evmap.adapter.PlaceAutocompleteAdapter
import net.vonforst.evmap.api.chargeprice.ChargepriceApi
import net.vonforst.evmap.autocomplete.ApiUnavailableException
import net.vonforst.evmap.autocomplete.PlaceWithBounds
import net.vonforst.evmap.bold
import net.vonforst.evmap.databinding.FragmentMapBinding
import net.vonforst.evmap.location.FusionEngine
import net.vonforst.evmap.location.LocationEngine
import net.vonforst.evmap.location.Priority
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.ChargepointListItem
import net.vonforst.evmap.model.ChargerPhoto
import net.vonforst.evmap.model.FILTERS_CUSTOM
import net.vonforst.evmap.model.FILTERS_DISABLED
import net.vonforst.evmap.model.FILTERS_FAVORITES
import net.vonforst.evmap.navigation.safeNavigate
import net.vonforst.evmap.shouldUseImperialUnits
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.HideOnScrollFabBehavior
import net.vonforst.evmap.ui.MarkerManager
import net.vonforst.evmap.ui.setTouchModal
import net.vonforst.evmap.utils.boundingBox
import net.vonforst.evmap.utils.checkAnyLocationPermission
import net.vonforst.evmap.utils.checkFineLocationPermission
import net.vonforst.evmap.utils.distanceBetween
import net.vonforst.evmap.utils.formatDecimal
import net.vonforst.evmap.viewmodel.GalleryViewModel
import net.vonforst.evmap.viewmodel.MapPosition
import net.vonforst.evmap.viewmodel.MapViewModel
import net.vonforst.evmap.viewmodel.Resource
import net.vonforst.evmap.viewmodel.Status
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.math.min


class MapFragment : Fragment(), OnMapReadyCallback, MenuProvider {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val vm: MapViewModel by viewModels()
    private val galleryVm: GalleryViewModel by activityViewModels()
    private var mapFragment: MapFragment? = null
    private var map: AnyMap? = null
    private var markerManager: MarkerManager? = null
    private lateinit var locationEngine: LocationEngine
    private var requestingLocationUpdates = false
    private lateinit var bottomSheetBehavior: BottomSheetBehaviorGoogleMapsLike<View>
    private lateinit var detailAppBarBehavior: MergedAppBarLayoutBehavior
    private lateinit var detailsDialog: ConnectorDetailsDialog
    private lateinit var prefs: PreferenceDataSource
    private var connectionErrorSnackbar: Snackbar? = null
    private var mapTopPadding: Int = 0
    private var mapBottomPadding: Int = 0
    private var popupMenu: PopupMenu? = null
    private var insetBottom: Int = 0
    private lateinit var favToggle: MenuItem
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val value = vm.layersMenuOpen.value
            if (value != null && value) {
                closeLayersMenu()
                return
            }

            if (vm.selectedChargepoint.value != null) {
                closeConnectorDetailsDialog()
                vm.selectedChargepoint.value = null
                return
            }

            if (binding.search.hasFocus()) {
                removeSearchFocus()
            }

            val state = bottomSheetBehavior.state
            when (state) {
                STATE_COLLAPSED -> vm.chargerSparse.value = null
                STATE_HIDDEN -> vm.searchResult.value = null
                else -> if (bottomSheetCollapsible) {
                    bottomSheetBehavior.state = STATE_COLLAPSED
                } else {
                    vm.chargerSparse.value = null
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceDataSource(requireContext())

        locationEngine = FusionEngine(requireContext())

        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    private val mapFragmentTag = "map"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(inflater, R.layout.fragment_map, container, false)
        println(binding.detailView.sourceButton)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.vm = vm

        val provider = prefs.mapProvider
        if (mapFragment == null) {
            mapFragment =
                childFragmentManager.findFragmentByTag(mapFragmentTag) as MapFragment?
        }
        if (mapFragment == null || mapFragment!!.priority[0] != getMapProvider(provider)) {
            mapFragment = MapFragment()
            mapFragment!!.priority = arrayOf(
                getMapProvider(provider),
                MapFactory.GOOGLE,
                MapFactory.MAPLIBRE
            )
            childFragmentManager
                .beginTransaction()
                .replace(R.id.map, mapFragment!!, mapFragmentTag)
                .commit()

            map = null
            markerManager = null
        }

        binding.detailAppBar.toolbar.popupTheme =
            com.google.android.material.R.style.Theme_Material3_DayNight

        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailAppBar.toolbar) { v, insets ->
            val systemWindowInsetTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemWindowInsetTop
            }
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.fabLayers) { v, insets ->
            // margin of layers button: status bar height + toolbar height + margin
            val systemWindowInsetTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val margin =
                if (binding.toolbarContainer.layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                    systemWindowInsetTop + (48 * density).toInt() + (28 * density).toInt()
                } else {
                    systemWindowInsetTop + (12 * density).toInt()
                }
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = margin
            }
            binding.layersSheet.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = margin
            }

            // set map padding so that compass is not obstructed by toolbar
            mapTopPadding = systemWindowInsetTop + (48 * density).toInt() + (16 * density).toInt()
            mapBottomPadding = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            // if we actually use map.setPadding here, MapLibre will re-trigger onApplyWindowInsets
            // and cause an infinite loop. So we rely on onMapReady being called later than
            // onApplyWindowInsets.

            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.fabLocate) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin =
                    systemBars + resources.getDimensionPixelSize(com.mahc.custombottomsheetbehavior.R.dimen.fab_margin)
            }
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.navBarScrim) { v, insets ->
            insetBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.layoutParams.height = insetBottom
            updatePeekHeight()
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.galleryContainer) { v, insets ->
            val systemWindowInsetTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val newHeight =
                resources.getDimensionPixelSize(R.dimen.gallery_height_with_margin) + systemWindowInsetTop
            v.layoutParams.height = newHeight
            bottomSheetBehavior.anchorPoint = newHeight
            WindowInsetsCompat.CONSUMED
        }

        exitTransition = TransitionInflater.from(requireContext())
            .inflateTransition(R.transition.map_exit_transition)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )

        return binding.root
    }

    private fun updatePeekHeight() {
        bottomSheetBehavior.peekHeight = binding.detailView.topPart.bottom + insetBottom
    }

    private fun getMapProvider(provider: String) = when (provider) {
        "mapbox" -> MapFactory.MAPLIBRE
        "google" -> MapFactory.GOOGLE
        else -> null
    }

    val bottomSheetCollapsible
        get() = resources.getBoolean(R.bool.bottom_sheet_collapsible)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!prefs.welcomeDialogShown || !prefs.dataSourceSet || !prefs.privacyAccepted) {
            findNavController().navigate(R.id.onboarding)
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        mapFragment!!.getMapAsync(this)
        bottomSheetBehavior = from(binding.bottomSheet)
        detailAppBarBehavior = MergedAppBarLayoutBehavior.from(binding.detailAppBar)

        binding.detailAppBar.toolbar.inflateMenu(R.menu.detail)
        favToggle = binding.detailAppBar.toolbar.menu.findItem(R.id.menu_fav)

        vm.apiName.observe(viewLifecycleOwner) {
            binding.detailAppBar.toolbar.menu.findItem(R.id.menu_edit).title =
                getString(R.string.edit_at_datasource, it)
        }

        binding.detailView.topPart.doOnNextLayout {
            updatePeekHeight()
            vm.bottomSheetState.value?.let { bottomSheetBehavior.state = it }
        }
        bottomSheetBehavior.isCollapsible = bottomSheetCollapsible
        binding.detailView.connectorDetails

        setupObservers()
        setupClickListeners()
        setupAdapters()
        (activity as? MapsActivity)?.setSupportActionBar(binding.toolbar)

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        if (prefs.appStartCounter > 5 && Duration.between(
                prefs.opensourceDonationsDialogLastShown,
                Instant.now()
            ) > Duration.ofDays(30)
        ) {
            try {
                findNavController().safeNavigate(MapFragmentDirections.actionMapToOpensourceDonations())
            } catch (ignored: IllegalArgumentException) {
                // when there is already another navigation going on
            } catch (ignored: IllegalStateException) {
                // "no current navigation node"
            }
        }
        /*if (!prefs.update060AndroidAutoDialogShown) {
            try {
                navController.safeNavigate(MapFragmentDirections.actionMapToUpdate060AndroidAuto())
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
                    if (_binding == null) return@withEndAction
                    binding.appLogo.root.animate().alpha(0f).apply {
                        startDelay = 1000
                    }.withEndAction {
                        if (_binding == null) return@withEndAction
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

        detailsDialog =
            ConnectorDetailsDialog(binding.detailView.connectorDetails, requireContext()) {
                closeConnectorDetailsDialog()
                vm.selectedChargepoint.value = null
            }
    }

    override fun onResume() {
        super.onResume()
        vm.reloadPrefs()
        if (requestingLocationUpdates && requireContext().checkAnyLocationPermission()
        ) {
            requestLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val context = context ?: return@registerForActivityResult
            if (context.checkAnyLocationPermission()) {
                enableLocation(moveTo = true, animate = true)
            }
        }

    private fun setupClickListeners() {
        binding.fabLocate.setOnClickListener {
            if (!requireContext().checkFineLocationPermission()) {
                requestPermissionLauncher.launch(
                    arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
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
                    (requireActivity() as MapsActivity).navigateTo(charger, binding.root)
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
                (activity as? MapsActivity)?.openUrl(charger.url ?: charger.dataSourceUrl, binding.root, true)
            }
        }
        binding.detailView.btnChargeprice.setOnClickListener {
            val charger = vm.charger.value?.data ?: return@setOnClickListener
            if (prefs.chargepriceNativeIntegration) {
                val extras =
                    FragmentNavigatorExtras(binding.detailView.btnChargeprice to getString(R.string.shared_element_chargeprice))
                findNavController().safeNavigate(
                    MapFragmentDirections.actionMapToChargepriceFragment(charger),
                    extras
                )
            } else {
                (activity as? MapsActivity)?.openUrl(
                    ChargepriceApi.getPoiUrl(charger),
                    binding.root
                )
            }
        }
        binding.detailView.btnChargerWebsite.setOnClickListener {
            val charger = vm.charger.value?.data ?: return@setOnClickListener
            charger.chargerUrl?.let { (activity as? MapsActivity)?.openUrl(it, binding.root) }
        }
        binding.detailView.btnLogin.setOnClickListener {
            findNavController().safeNavigate(
                MapFragmentDirections.actionMapToDataSettings(true)
            )
        }
        binding.detailView.imgPredictionSource.setOnClickListener {
            (activity as? MapsActivity)?.openUrl(getString(R.string.fronyx_url), binding.root)
        }
        binding.detailView.btnPredictionHelp.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.prediction_help))
                .setPositiveButton(R.string.ok) { _, _ -> }
                .show()
        }
        binding.detailView.topPart.setOnClickListener {
            bottomSheetBehavior.state = STATE_ANCHOR_POINT
        }
        binding.detailView.topPart.setOnLongClickListener {
            val charger = vm.charger.value?.data ?: return@setOnLongClickListener false
            copyToClipboard(ClipData.newPlainText(getString(R.string.charger_name), charger.name))
            return@setOnLongClickListener true
        }
        setupSearchAutocomplete()
        binding.detailAppBar.toolbar.setNavigationOnClickListener {
            if (bottomSheetCollapsible) {
                bottomSheetBehavior.state = STATE_COLLAPSED
            } else {
                vm.chargerSparse.value = null
            }
        }
        binding.detailAppBar.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_fav -> {
                    toggleFavorite()
                    true
                }
                R.id.menu_share -> {
                    val charger = vm.charger.value?.data
                    if (charger != null && charger.url != null) {
                        (activity as? MapsActivity)?.shareUrl(charger.url)
                    }
                    true
                }
                R.id.menu_edit -> {
                    val charger = vm.charger.value?.data
                    if (charger?.editUrl != null) {
                        val uri = Uri.parse(charger.editUrl)
                        if (uri.getScheme() == "mailto") {
                            val intent = Intent(Intent.ACTION_SENDTO, uri)
                            try {
                                startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(
                                    requireContext(),
                                    R.string.no_email_app_found,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        else {
                            (activity as? MapsActivity)?.openUrl(charger.editUrl, binding.root, true)
                        }

                        if (vm.apiId.value == "goingelectric") {
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

                R.id.menu_reload -> {
                    vm.reloadChargerDetails()
                    true
                }

                else -> false
            }
        }
        binding.detailView.btnRefreshLiveData.setOnClickListener {
            vm.reloadAvailability()
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
        binding.search.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.search.keyListener = searchKeyListener
                binding.search.text = binding.search.text  // workaround to fix copy/paste
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

        binding.fabLayers.postDelayed({
            val materialTransform = MaterialContainerTransform().apply {
                startView = binding.fabLayers
                endView = binding.layersSheet
                setPathMotion(MaterialArcMotion())
                duration = 250
                scrimColor = Color.TRANSPARENT
                addTarget(binding.layersSheet)
                isElevationShadowEnabled = false
            }
            TransitionManager.beginDelayedTransition(binding.root, materialTransform)
            vm.layersMenuOpen.value = true
        }, 100)
    }

    private fun closeLayersMenu() {
        binding.fabLayers.tag = true

        binding.fabLayers.postDelayed({
            val materialTransform = MaterialContainerTransform().apply {
                startView = binding.layersSheet
                endView = binding.fabLayers
                setPathMotion(MaterialArcMotion())
                duration = 200
                scrimColor = Color.TRANSPARENT
                addTarget(binding.fabLayers)
                isElevationShadowEnabled = false
            }
            TransitionManager.beginDelayedTransition(binding.root, materialTransform)
            vm.layersMenuOpen.value = false
        }, 100)
    }

    private fun toggleFavorite() {
        val favs = vm.favorites.value ?: return
        val charger = vm.chargerSparse.value ?: return
        val fav = favs.find { it.charger.id == charger.id }
        if (fav != null) {
            vm.deleteFavorite(fav.favorite)
        } else {
            vm.insertFavorite(charger)
        }
    }

    private fun setupObservers() {
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (bottomSheetBehavior.state == STATE_HIDDEN) {
                    map?.setPadding(0, mapTopPadding, 0, mapBottomPadding)
                } else {
                    val height = binding.root.height - bottomSheet.top
                    map?.setPadding(
                        0,
                        mapTopPadding,
                        0,
                        mapBottomPadding + min(bottomSheetBehavior.peekHeight, height)
                    )
                }
                println(slideOffset)
                if (bottomSheetBehavior.state != STATE_HIDDEN) {
                    binding.navBarScrim.visibility = View.VISIBLE
                    binding.navBarScrim.translationY =
                        (if (slideOffset < 0f) -slideOffset else 2 * slideOffset) * binding.navBarScrim.height
                } else {
                    binding.navBarScrim.visibility = View.INVISIBLE
                }
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                vm.bottomSheetState.value = newState
                updateBackPressedCallback()

                if (vm.layersMenuOpen.value!! && newState !in listOf(
                        STATE_SETTLING,
                        STATE_HIDDEN,
                        STATE_COLLAPSED
                    )
                ) {
                    closeLayersMenu()
                }

                if (vm.selectedChargepoint.value != null && newState in listOf(
                        STATE_ANCHOR_POINT, STATE_COLLAPSED
                    )
                ) {
                    closeConnectorDetailsDialog()
                    vm.selectedChargepoint.value = null
                }
            }
        })
        vm.chargerSparse.observe(viewLifecycleOwner) {
            if (it != null) {
                if (vm.bottomSheetState.value != STATE_ANCHOR_POINT) {
                    bottomSheetBehavior.state =
                        if (bottomSheetCollapsible) STATE_COLLAPSED else STATE_ANCHOR_POINT
                }
                removeSearchFocus()
                binding.fabDirections.show()
                detailAppBarBehavior.setToolbarTitle(it.name)
                updateFavoriteToggle()
                markerManager?.highlighedCharger = it
                markerManager?.animateBounce(it)
            } else {
                bottomSheetBehavior.state = STATE_HIDDEN
                markerManager?.highlighedCharger = null
            }
        }
        vm.chargepoints.observe(viewLifecycleOwner, Observer { res ->
            val chargepoints = res.data
            if (chargepoints != null) {
                markerManager?.chargepoints = chargepoints
            }
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
        })
        vm.useMiniMarkers.observe(viewLifecycleOwner) {
            markerManager?.mini = it
        }
        vm.filteredConnectors.observe(viewLifecycleOwner) {
            markerManager?.filteredConnectors = it
        }
        vm.favorites.observe(viewLifecycleOwner) {
            updateFavoriteToggle()
            markerManager?.favorites = it.map { it.favorite.chargerId }.toSet()
        }
        vm.searchResult.observe(viewLifecycleOwner) { place ->
            displaySearchResult(place, moveCamera = true)
        }
        vm.layersMenuOpen.observe(viewLifecycleOwner) { open ->
            HideOnScrollFabBehavior.from(binding.fabLayers)?.hidden = open
            binding.fabLayers.visibility = if (open) View.INVISIBLE else View.VISIBLE
            binding.layersSheet.visibility = if (open) View.VISIBLE else View.INVISIBLE
            updateBackPressedCallback()
        }
        vm.mapType.observe(viewLifecycleOwner) {
            map?.setMapType(it)
        }
        vm.mapTrafficEnabled.observe(viewLifecycleOwner) {
            map?.setTrafficEnabled(it)
        }
        vm.selectedChargepoint.observe(viewLifecycleOwner) {
            binding.detailView.connectorDetailsCard.visibility =
                if (it != null) View.VISIBLE else View.INVISIBLE
            if (it != null) {
                detailsDialog.setData(it, vm.availability.value?.data)
            }
            updateBackPressedCallback()
        }

        updateBackPressedCallback()
    }

    private fun displaySearchResult(place: PlaceWithBounds?, moveCamera: Boolean) {
        val map = this.map ?: return
        markerManager?.searchResult = place

        if (place != null) {
            // disable location following when search result is shown
            if (moveCamera) {
                vm.myLocationEnabled.value = false
                if (place.viewport != null) {
                    map.animateCamera(map.cameraUpdateFactory.newLatLngBounds(place.viewport, 0))
                } else {
                    map.animateCamera(map.cameraUpdateFactory.newLatLngZoom(place.latLng, 12f))
                }
            }
        } else {
            binding.search.setText("")
        }

        updateBackPressedCallback()
    }

    private fun updateBackPressedCallback() {
        backPressedCallback.isEnabled =
            vm.bottomSheetState.value != null && vm.bottomSheetState.value != STATE_HIDDEN
                    || vm.searchResult.value != null
                    || (vm.layersMenuOpen.value ?: false)
                    || binding.search.hasFocus()
                    || vm.selectedChargepoint.value != null
    }

    private fun updateFavoriteToggle() {
        val favs = vm.favorites.value ?: return
        val charger = vm.chargerSparse.value ?: return
        if (favs.find { it.charger.id == charger.id } != null) {
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
                    imageView.load(photo.getUrl(size = 1000, allowOriginal = true)) {
                        if (photo == photos[position] && imageCacheKey != null) {
                            placeholderMemoryCacheKey(imageCacheKey)
                        }
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
                    .withHiddenStatusBar(false)
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
            adapter = ConnectorAdapter().apply {
                onClickListener = {
                    vm.selectedChargepoint.value = it.chargepoint
                    openConnectorDetailsDialog()
                }
            }
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
                                (activity as? MapsActivity)?.showLocation(charger, binding.root)
                            }
                            R.drawable.ic_fault_report -> {
                                if (charger.url != null) {
                                    (activity as? MapsActivity)?.openUrl(
                                        charger.url,
                                        binding.root,
                                        true
                                    )
                                }
                            }

                            R.drawable.ic_payment -> {
                                showPaymentMethodsDialog(charger)
                            }

                            R.drawable.ic_network -> {
                                charger.networkUrl?.let {
                                    (activity as? MapsActivity)?.openUrl(
                                        it,
                                        binding.root
                                    )
                                }
                            }
                        }
                    }
                }
                onLongClickListener = {
                    val charger = vm.chargerDetails.value?.data
                    if (charger != null) {
                        when (it.icon) {
                            R.drawable.ic_address -> {
                                if (charger.address != null) {
                                    copyToClipboard(ClipData.newPlainText(
                                        getString(R.string.address),
                                        charger.address.toString()
                                    ))
                                    true
                                } else {
                                    false
                                }
                            }
                            R.drawable.ic_location -> {
                                copyToClipboard(ClipData.newPlainText(
                                    getString(R.string.coordinates),
                                    charger.coordinates.formatDecimal()
                                ))
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
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

    private fun copyToClipboard(clip: ClipData) {
        val clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(clip)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Snackbar.make(
                requireView(),
                R.string.copied,
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }

    private fun openConnectorDetailsDialog() {
        val chargepoints = vm.chargerDetails.value?.data?.chargepointsMerged ?: return
        val chargepoint = vm.selectedChargepoint.value ?: return
        val index = chargepoints.indexOf(chargepoint).takeIf { it >= 0 } ?: return
        val vh = binding.detailView.connectors.findViewHolderForAdapterPosition(index) ?: return

        val materialTransform = MaterialContainerTransform().apply {
            startView = vh.itemView
            endView = binding.detailView.connectorDetailsCard
            setPathMotion(MaterialArcMotion())
            duration = 250
            scrimColor = Color.TRANSPARENT
            addTarget(binding.detailView.connectorDetailsCard)
            isElevationShadowEnabled = false
            fadeMode = FADE_MODE_CROSS
        }
        TransitionManager.beginDelayedTransition(binding.root, materialTransform)
    }

    private fun closeConnectorDetailsDialog() {
        val chargepoints = vm.chargerDetails.value?.data?.chargepointsMerged ?: return
        val chargepoint = vm.selectedChargepoint.value ?: return
        val index = chargepoints.indexOf(chargepoint).takeIf { it >= 0 } ?: return
        val vh = binding.detailView.connectors.findViewHolderForAdapterPosition(index) ?: return

        val materialTransform = MaterialContainerTransform().apply {
            startView = binding.detailView.connectorDetailsCard
            endView = vh.itemView
            setPathMotion(MaterialArcMotion())
            duration = 200
            scrimColor = Color.TRANSPARENT
            addTarget(vh.itemView)
            isElevationShadowEnabled = false
            fadeMode = FADE_MODE_CROSS
        }
        TransitionManager.beginDelayedTransition(binding.root, materialTransform)
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
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.charge_cards)
            .setItems(names.toTypedArray()) { _, i ->
                val card = data[i]
                (activity as? MapsActivity)?.openUrl("https:${card.url}", binding.root)
            }.show()
    }

    override fun onMapReady(map: AnyMap) {
        this.map = map
        val context = this.context ?: return
        view ?: return
        markerManager = MarkerManager(context, map, this).apply {
            onChargerClick = {
                vm.chargerSparse.value = it
            }
            onClusterClick = {
                val newZoom = map.cameraPosition.zoom + 2
                map.animateCamera(
                    map.cameraUpdateFactory.newLatLngZoom(
                        LatLng(it.coordinates.lat, it.coordinates.lng),
                        newZoom
                    )
                )
            }
            chargepoints = vm.chargepoints.value?.data ?: emptyList()
            highlighedCharger = vm.chargerSparse.value
            searchResult = vm.searchResult.value
            favorites = vm.favorites.value?.map { it.favorite.chargerId }?.toSet() ?: emptySet()
        }

        map.uiSettings.setTiltGesturesEnabled(false)
        map.uiSettings.setRotateGesturesEnabled(prefs.mapRotateGesturesEnabled)
        map.setIndoorEnabled(false)
        map.uiSettings.setIndoorLevelPickerEnabled(false)
        map.uiSettings.setMapToolbarEnabled(false)

        map.setOnCameraIdleListener {
            vm.mapPosition.value = MapPosition(
                map.projection.visibleRegion.latLngBounds, map.cameraPosition.zoom
            )
            vm.reloadChargepoints()
        }
        map.setOnCameraMoveListener {
            vm.mapPosition.value = MapPosition(
                map.projection.visibleRegion.latLngBounds, map.cameraPosition.zoom
            )
        }

        binding.scaleView.apply {
            if (prefs.showMapScale) {
                visibility = View.VISIBLE
                if (prefs.mapScaleMetersAndMiles) {
                    metersAndMiles()
                } else {
                    if (shouldUseImperialUnits(requireContext())) {
                        milesOnly()
                    } else {
                        metersOnly()
                    }
                }
            } else {
                visibility = View.GONE
            }
        }
        vm.mapPosition.observe(viewLifecycleOwner) {
            val target = map.cameraPosition.target ?: return@observe
            binding.scaleView.update(map.cameraPosition.zoom, target.latitude)
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
        map.setOnMapClickListener {
            if (backPressedCallback.isEnabled) {
                backPressedCallback.handleOnBackPressed()
            }
        }
        map.setMapType(vm.mapType.value)
        map.setTrafficEnabled(vm.mapTrafficEnabled.value ?: false)

        // set padding so that compass is not obstructed by toolbar
        map.setPadding(0, mapTopPadding, 0, mapBottomPadding)

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
                object : Observer<ChargeLocation?> {
                    override fun onChanged(value: ChargeLocation?) {
                        if (value?.id == chargerId) {
                            val cameraUpdate = map.cameraUpdateFactory.newLatLngZoom(
                                LatLng(value.coordinates.lat, value.coordinates.lng), 16f
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
                        override fun onChanged(value: Resource<List<ChargepointListItem>>) {
                            if (value.data == null) return
                            for (item in value.data) {
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
                locationName?.let { binding.search.setText(it) }
            }

            positionSet = true
        } else if (locationName != null) {
            binding.search.setText(locationName)
            binding.search.requestFocus()
            binding.search.setSelection(locationName.length)
        }
        if (context.checkAnyLocationPermission()) {
            if (prefs.currentMapMyLocationEnabled && !positionSet) {
                enableLocation(true, false)
                positionSet = true
            } else {
                enableLocation(false, false)
            }
        }
        if (!positionSet) {
            // use position saved in preferences, fall back to default (Europe)
            val cameraUpdate =
                map.cameraUpdateFactory.newLatLngZoom(
                    prefs.currentMapLocation,
                    prefs.currentMapZoom
                )
            map.moveCamera(cameraUpdate)
        }

        vm.mapPosition.value = MapPosition(
            map.projection.visibleRegion.latLngBounds, map.cameraPosition.zoom
        )

        if (vm.searchResult.value != null) {
            // show search result (after configuration change)
            displaySearchResult(vm.searchResult.value, moveCamera = !positionSet)
        }
    }

    @RequiresPermission(anyOf = [ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION])
    private fun enableLocation(moveTo: Boolean, animate: Boolean) {
        val map = this.map ?: return
        map.setMyLocationEnabled(true)
        map.uiSettings.setMyLocationButtonEnabled(false)
        if (moveTo) {
            vm.myLocationEnabled.value = true
            moveToLastLocation(map, animate)
            requestLocationUpdates()
        }
    }

    @RequiresPermission(anyOf = [ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION])
    private fun moveToLastLocation(map: AnyMap, animate: Boolean) {
        val location = locationEngine.getLastKnownLocation()
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

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map, menu)

        val filterItem = menu.findItem(R.id.menu_filter)
        val filterView = filterItem.actionView

        val filterBadge = filterView?.findViewById<TextView>(R.id.filter_badge)
        if (filterBadge != null) {
            // set up badge showing number of active filters
            vm.filtersCount.observe(viewLifecycleOwner) {
                filterBadge.visibility = if (it > 0) View.VISIBLE else View.GONE
                filterBadge.text = it.toString()
            }
        }
        filterView?.setOnClickListener {
            val profilesMap: MutableBiMap<Long, MenuItem> = HashBiMap()

            val popup = PopupMenu(
                ContextThemeWrapper(requireContext(), R.style.RoundedPopup),
                it,
                Gravity.END
            )
            popup.menuInflater.inflate(R.menu.popup_filter, popup.menu)
            MenuCompat.setGroupDividerEnabled(popup.menu, true)
            popup.setForceShowIcon(true)
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_edit_filters -> {
                        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
                        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
                        lifecycleScope.launch {
                            vm.copyFiltersToCustom()
                            findNavController().safeNavigate(
                                MapFragmentDirections.actionMapToFilterFragment()
                            )
                        }
                        true
                    }

                    R.id.menu_manage_filter_profiles -> {
                        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true)
                        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
                        findNavController().safeNavigate(
                            MapFragmentDirections.actionMapToFilterProfilesFragment()
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

            vm.filterProfiles.observe(viewLifecycleOwner) { profiles ->
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

                popup.menu.setGroupCheckable(R.id.menu_group_filter_profiles, true, true)

                val manageFiltersItem = popup.menu.findItem(R.id.menu_manage_filter_profiles)
                manageFiltersItem.isVisible = profiles.isNotEmpty()

                vm.filterStatus.observe(viewLifecycleOwner) { id ->
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
                }
            }
            popup.setTouchModal(false)
            popupMenu = popup
            popup.show()
        }

        filterView?.setOnLongClickListener {
            // enable/disable filters
            vm.toggleFilters()
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


    override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
        R.id.menu_reload -> {
            vm.reloadChargepoints(true)
            true
        }

        else -> false
    }

    @RequiresPermission(anyOf = [ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION])
    private fun requestLocationUpdates() {
        locationEngine.requestLocationUpdates(
            Priority.HIGH_ACCURACY,
            5000,
            locationListener
        )
        requestingLocationUpdates = true
    }

    @SuppressLint("MissingPermission")
    private fun removeLocationUpdates() {
        if (context?.checkAnyLocationPermission() == true) {
            locationEngine.removeUpdates(locationListener)
        }
    }

    private val locationListener = LocationListenerCompat { location ->
        val map = this.map ?: return@LocationListenerCompat
        if (vm.myLocationEnabled.value == false) return@LocationListenerCompat

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
        vm.mapPosition.value?.let {
            prefs.currentMapLocation = it.bounds.center
            prefs.currentMapZoom = it.zoom
        }
        vm.myLocationEnabled.value?.let {
            prefs.currentMapMyLocationEnabled = it
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detailsDialog.onDestroy()

        map = null
        mapFragment = null
        _binding = null
        markerManager = null
        /* if we don't dismiss the popup menu, it will be recreated in some cases
        (split-screen mode) and then have references to a destroyed fragment. */
        popupMenu?.dismiss()
    }
}