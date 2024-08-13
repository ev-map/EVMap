package net.vonforst.evmap.auto

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import androidx.activity.OnBackPressedCallback
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.annotations.RequiresCarApi
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.car2go.maps.AnyMap
import com.car2go.maps.OnMapReadyCallback
import com.car2go.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.AvailabilityRepository
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.ChargeLocationCluster
import net.vonforst.evmap.model.ChargepointListItem
import net.vonforst.evmap.model.FILTERS_FAVORITES
import net.vonforst.evmap.model.FilterValue
import net.vonforst.evmap.model.FilterWithValue
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.ChargeLocationsRepository
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.MarkerManager
import net.vonforst.evmap.utils.distanceBetween
import net.vonforst.evmap.viewmodel.Status
import net.vonforst.evmap.viewmodel.await
import net.vonforst.evmap.viewmodel.awaitFinished
import net.vonforst.evmap.viewmodel.filtersWithValue
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.collections.set
import kotlin.math.min

/**
 * Main map screen showing either nearby chargers or favorites.
 *
 * New implementation for Car App API Level >= 7 with interactive map using MapSurfaceCallback
 */
@RequiresCarApi(7)
@ExperimentalCarApi
class MapScreen(ctx: CarContext, val session: EVMapSession) :
    Screen(ctx), LocationAwareScreen, ChargerListDelegate,
    DefaultLifecycleObserver, OnMapReadyCallback {
    companion object {
        val MARKER = "map"
    }

    private val db = AppDatabase.getInstance(carContext)
    private var prefs = PreferenceDataSource(ctx)
    private val repo =
        ChargeLocationsRepository(createApi(prefs.dataSource, ctx), lifecycleScope, db, prefs)
    private val availabilityRepo = AvailabilityRepository(ctx)

    private var updateCoroutine: Job? = null
    private var availabilityUpdateCoroutine: Job? = null

    private var visibleStart: Int? = null
    private var visibleEnd: Int? = null

    override var location: Location? = null
    private var lastDistanceUpdateTime: Instant? = null
    private var chargers: List<ChargepointListItem>? = null
    private var selectedCharger: ChargeLocation? = null
    private val favorites = db.favoritesDao().getAllFavorites()

    override var loadingError = false
    override val locationError = false

    private val mapSurfaceCallback = MapSurfaceCallback(carContext, lifecycleScope)

    private val distanceUpdateThreshold = Duration.ofSeconds(15)
    private val availabilityUpdateThreshold = Duration.ofMinutes(1)

    private var availabilities: MutableMap<Long, Pair<ZonedDateTime, ChargeLocationStatus?>> =
        HashMap()
    override val maxRows =
        min(ctx.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST), 25)

    override var filterStatus = prefs.filterStatus
    private var filtersWithValue: List<FilterWithValue<FilterValue>>? = null

    private val carInfo: CarInfo by lazy {
        (ctx.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager).carInfo
    }
    private val carSensors: CarSensors by lazy { carContext.patchedCarSensors }
    override var energyLevel: EnergyLevel? = null

    private var heading: Compass? = null
    private val permissions = if (BuildConfig.FLAVOR_automotive == "automotive") {
        listOf(
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_ENERGY_PORTS",
            "android.car.permission.READ_CAR_DISPLAY_UNITS",
        )
    } else {
        listOf(
            "com.google.android.gms.permission.CAR_FUEL"
        )
    }

    private var map: AnyMap? = null
    private var markerManager: MarkerManager? = null
    private var myLocationEnabled = false
    private var myLocationNeedsUpdate = false

    private val formatter = ChargerListFormatter(ctx, this)
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            clearSelectedCharger()
        }
    }

    init {
        lifecycle.addObserver(this)
        marker = MARKER

        favorites.observe(this) {
            val favoriteIds = it.map { it.favorite.chargerId }.toSet()
            markerManager?.favorites = favoriteIds
            formatter.favorites = favoriteIds
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java)
            .setSurfaceCallback(mapSurfaceCallback)

        carContext.onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onGetTemplate(): Template {
        session.mapScreen = this
        val map = map

        val title = prefs.placeSearchResultAndroidAutoName ?: carContext.getString(
            if (filterStatus == FILTERS_FAVORITES) {
                R.string.auto_favorites
            } else if (myLocationEnabled) {
                R.string.auto_chargers_closeby
            } else {
                R.string.app_name
            }
        )

        val actionStrip = buildActionStrip()
        val selectedCharger = selectedCharger

        val contentTemplate = if (selectedCharger != null) {
            PaneTemplate.Builder(
                formatter.buildSingleCharger(
                    selectedCharger,
                    availabilities.get(selectedCharger.id)?.second
                ) {
                    screenManager.push(ChargerDetailScreen(carContext, selectedCharger))
                    session.mapScreen = null
                }).apply {
                setHeader(Header.Builder().apply {
                    setTitle(selectedCharger.name)
                    setStartHeaderAction(Action.BACK)
                }.build())
            }.build()
        } else if (chargers?.filterIsInstance<ChargeLocationCluster>()?.isNotEmpty() == true) {
            MessageTemplate.Builder(carContext.getString(R.string.auto_zoom_for_details))
                .apply {
                    setHeader(Header.Builder().apply {
                        setTitle(title)
                        setStartHeaderAction(Action.APP_ICON)
                    }.build())
                }.build()
        } else {
            ListTemplate.Builder().apply {
                setHeader(Header.Builder().apply {
                    setTitle(title)
                    setStartHeaderAction(Action.APP_ICON)
                }.build())

                formatter.buildChargerList(
                    chargers?.filterIsInstance<ChargeLocation>(),
                    availabilities
                )?.let {
                    setSingleList(it)
                } ?: setLoading(true)
            }.build()
        }
        return MapWithContentTemplate.Builder().apply {
            setContentTemplate(contentTemplate)
            setActionStrip(actionStrip)
            setMapController(MapController.Builder().apply {
                setMapActionStrip(buildMapActionStrip())
                setPanModeListener { }
            }.build())
        }.build()
    }

    private fun buildMapActionStrip() = ActionStrip.Builder()
        .addAction(Action.PAN)
        .addAction(
            Action.Builder().setIcon(
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_location))
                    .setTint(if (myLocationEnabled) CarColor.SECONDARY else CarColor.DEFAULT)
                    .build()
            ).setOnClickListener {
                enableLocation(true)
            }.build()
        )
        .addAction(
            Action.Builder().setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        R.drawable.ic_add
                    )
                ).setTint(CarColor.DEFAULT).build()
            ).setOnClickListener {
                val map = map ?: return@setOnClickListener
                mapSurfaceCallback.animateCamera(map.cameraUpdateFactory.zoomBy(0.5f))
            }.build()
        )
        .addAction(
            Action.Builder().setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(
                        carContext,
                        R.drawable.ic_remove
                    )
                ).setTint(CarColor.DEFAULT).build()
            ).setOnClickListener {
                val map = map ?: return@setOnClickListener
                mapSurfaceCallback.animateCamera(map.cameraUpdateFactory.zoomBy(-0.5f))
            }.build()
        ).build()

    private fun buildActionStrip(): ActionStrip {
        val filtersCount = if (filterStatus == FILTERS_FAVORITES) 1 else {
            filtersWithValue?.count {
                !it.value.hasSameValueAs(it.filter.defaultValue())
            }
        }
        return ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_settings
                            )
                        ).setTint(CarColor.DEFAULT).build()
                    )
                    .setOnClickListener {
                        screenManager.push(SettingsScreen(carContext, session))
                        session.mapScreen = null
                    }
                    .build())
            .addAction(Action.Builder().apply {
                setIcon(
                    CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            if (prefs.placeSearchResultAndroidAuto != null) {
                                R.drawable.ic_search_off
                            } else {
                                R.drawable.ic_search
                            }
                        )
                    ).build()

                )
                setOnClickListener {
                    if (prefs.placeSearchResultAndroidAuto != null) {
                        prefs.placeSearchResultAndroidAutoName = null
                        prefs.placeSearchResultAndroidAuto = null
                        markerManager?.searchResult = null
                        invalidate()
                    } else {
                        screenManager.pushForResult(
                            PlaceSearchScreen(
                                carContext,
                                session
                            )
                        ) {
                            chargers = null
                            loadChargers()
                        }
                        session.mapScreen = null
                    }
                }
            }.build())
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_filter
                            )
                        )
                            .setTint(if (filtersCount != null && filtersCount > 0) CarColor.SECONDARY else CarColor.DEFAULT)
                            .build()
                    )
                    .setOnClickListener {
                        screenManager.push(FilterScreen(carContext, session))
                        session.mapScreen = null
                    }
                    .build())
            .build()
    }

    override fun onChargerClick(charger: ChargeLocation) {
        selectedCharger = charger
        markerManager?.highlighedCharger = charger
        markerManager?.animateBounce(charger)
        backPressedCallback.isEnabled = true
        invalidate()
        // load availability
        lifecycleScope.launch {
            val availability = availabilityRepo.getAvailability(charger).data
            val date = ZonedDateTime.now()
            availabilities[charger.id] = date to availability
            invalidate()
        }
    }

    fun clearSelectedCharger() {
        selectedCharger = null
        markerManager?.highlighedCharger = null
        backPressedCallback.isEnabled = false
        invalidate()
    }

    override fun updateLocation(location: Location) {
        if (location.latitude == this.location?.latitude
            && location.longitude == this.location?.longitude
        ) {
            return
        }
        val oldLoc = this.location?.let { LatLng.fromLocation(it) }
        val latLng = LatLng.fromLocation(location)
        this.location = location

        val map = map ?: return
        if (myLocationEnabled) {
            if (oldLoc == null) {
                mapSurfaceCallback.animateCamera(map.cameraUpdateFactory.newLatLngZoom(latLng, 13f))
            } else if (latLng != oldLoc && distanceBetween(
                    latLng.latitude,
                    latLng.longitude,
                    oldLoc.latitude,
                    oldLoc.longitude
                ) > 1
            ) {
                // only update map if location changed by more than 1 meter
                val camUpdate = map.cameraUpdateFactory.newLatLng(latLng)
                mapSurfaceCallback.animateCamera(camUpdate)
            }
        }

        val now = Instant.now()
        if (lastDistanceUpdateTime == null ||
            Duration.between(lastDistanceUpdateTime, now) > distanceUpdateThreshold
        ) {
            lastDistanceUpdateTime = now
            // update displayed distances
            invalidate()
        }
    }

    private fun loadChargers() {
        val location = location ?: return
        val map = map ?: return

        updateCoroutine = lifecycleScope.launch {
            loadingError = false
            try {
                filterStatus = prefs.filterStatus
                val filterValues =
                    db.filterValueDao().getFilterValuesAsync(filterStatus, prefs.dataSource)
                val filters = repo.getFiltersAsync(carContext.stringProvider())
                filtersWithValue = filtersWithValue(filters, filterValues)

                // load chargers
                if (filterStatus == FILTERS_FAVORITES) {
                    val chargers = favorites.await().map { it.charger }.sortedBy {
                        distanceBetween(
                            location.latitude, location.longitude,
                            it.coordinates.lat, it.coordinates.lng
                        )
                    }
                    this@MapScreen.chargers = chargers
                } else {
                    val response = repo.getChargepoints(
                        map.projection.visibleRegion.latLngBounds,
                        map.cameraPosition.zoom,
                        filtersWithValue,
                        false
                    ).awaitFinished()
                    if (response.status == Status.ERROR || response.data == null) {
                        loadingError = true
                        this@MapScreen.chargers = null
                        invalidate()
                        return@launch
                    }
                    this@MapScreen.chargers = response.data
                    markerManager?.chargepoints = response.data
                }

                updateCoroutine = null
                lastDistanceUpdateTime = Instant.now()
                invalidate()
            } catch (e: IOException) {
                loadingError = true
                invalidate()
            } catch (e: HttpException) {
                loadingError = true
                invalidate()
            }
        }
    }

    private fun onEnergyLevelUpdated(energyLevel: EnergyLevel) {
        val isUpdate = this.energyLevel == null
        this.energyLevel = energyLevel
        if (isUpdate) invalidate()
    }

    private fun onCompassUpdated(compass: Compass) {
        this.heading = compass
    }

    override fun onStart(owner: LifecycleOwner) {
        mapSurfaceCallback.getMapAsync(this)
        setupListeners()
        session.requestLocationUpdates()

        // Reloading chargers in onStart does not seem to count towards content limit.
        // So let's do this so the user gets fresh chargers when re-entering the app.
        if (prefs.dataSource != repo.api.value?.id) {
            repo.api.value = createApi(prefs.dataSource, carContext)
        }
        invalidate()
    }

    private fun setupListeners() {
        val exec = ContextCompat.getMainExecutor(carContext)
        if (supportsCarApiLevel3(carContext)) {
            carSensors.addCompassListener(
                CarSensors.UPDATE_RATE_NORMAL,
                exec,
                ::onCompassUpdated
            )
        }
        if (!permissions.all {
                ContextCompat.checkSelfPermission(
                    carContext,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            })
            return

        if (supportsCarApiLevel3(carContext)) {
            println("Setting up energy level listener")
            carInfo.addEnergyLevelListener(exec, ::onEnergyLevelUpdated)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // Reloading chargers in onStart does not seem to count towards content limit.
        // So let's do this so the user gets fresh chargers when re-entering the app.
        // Deleting the data already in onStop makes sure that we show a loading screen directly
        // (i.e. onGetTemplate is not called while the old data is still there)
        chargers = null
        availabilities.clear()
        location = null
        myLocationEnabled = false
        removeListeners()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)

        map?.let {
            prefs.currentMapLocation = it.cameraPosition.target
            prefs.currentMapZoom = it.cameraPosition.zoom
        }
        prefs.currentMapMyLocationEnabled = myLocationEnabled
    }

    private fun removeListeners() {
        if (supportsCarApiLevel3(carContext)) {
            println("Removing energy level listener")
            carInfo.removeEnergyLevelListener(::onEnergyLevelUpdated)
            carSensors.removeCompassListener(::onCompassUpdated)
        }
    }

    override fun onItemVisibilityChanged(startIndex: Int, endIndex: Int) {
        // when the list is scrolled, load corresponding availabilities
        if (startIndex == visibleStart && endIndex == visibleEnd && availabilities.isNotEmpty()) return
        if (startIndex == -1 || endIndex == -1) return
        if (availabilityUpdateCoroutine != null) return

        visibleEnd = endIndex
        visibleStart = startIndex

        // remove outdated availabilities
        availabilities = availabilities.filter {
            Duration.between(
                it.value.first,
                ZonedDateTime.now()
            ) <= availabilityUpdateThreshold
        }.toMutableMap()

        // update availabilities
        availabilityUpdateCoroutine = lifecycleScope.launch {
            delay(300L)

            val chargers = chargers?.filterIsInstance(ChargeLocation::class.java) ?: return@launch
            if (chargers.isEmpty()) return@launch

            val tasks = chargers.subList(
                min(startIndex, chargers.size - 1),
                min(endIndex, chargers.size - 1)
            ).mapNotNull {
                // update only if not yet stored
                if (!availabilities.containsKey(it.id)) {
                    lifecycleScope.async {
                        val availability = availabilityRepo.getAvailability(it).data
                        val date = ZonedDateTime.now()
                        availabilities[it.id] = date to availability
                    }
                } else null
            }
            if (tasks.isNotEmpty()) {
                tasks.awaitAll()
                invalidate()
            }
            availabilityUpdateCoroutine = null
        }
    }

    override fun onMapReady(map: AnyMap) {
        this.map = map
        this.markerManager =
            MarkerManager(
                mapSurfaceCallback.presentation.context,
                map,
                this,
                markerHeight = if (BuildConfig.FLAVOR_automotive == "automotive") 36 else 64
            ).apply {
                this@MapScreen.chargers?.let { chargepoints = it }
                onChargerClick = this@MapScreen::onChargerClick
                onClusterClick = {
                    val newZoom = map.cameraPosition.zoom + 2
                    mapSurfaceCallback.animateCamera(
                        map.cameraUpdateFactory.newLatLngZoom(
                            LatLng(it.coordinates.lat, it.coordinates.lng),
                            newZoom
                        )
                    )
                }
                searchResult = prefs.placeSearchResultAndroidAuto
                highlighedCharger = selectedCharger
            }

        map.setMyLocationEnabled(true)
        map.uiSettings.setMyLocationButtonEnabled(false)
        map.uiSettings.setMapToolbarEnabled(false)
        map.setAttributionClickListener { attributions ->
            screenManager.push(MapAttributionScreen(carContext, attributions))
        }
        map.setOnMapClickListener {
            clearSelectedCharger()
        }

        val mode = carContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        map.setMapStyle(
            if (mode == Configuration.UI_MODE_NIGHT_YES) AnyMap.Style.DARK else AnyMap.Style.NORMAL
        )

        prefs.placeSearchResultAndroidAuto?.let { place ->
            // move to the location of the search result
            myLocationEnabled = false
            markerManager?.searchResult = place
            if (place.viewport != null) {
                map.moveCamera(map.cameraUpdateFactory.newLatLngBounds(place.viewport, 0))
            } else {
                map.moveCamera(map.cameraUpdateFactory.newLatLngZoom(place.latLng, 12f))
            }
        } ?: if (prefs.currentMapMyLocationEnabled) {
            enableLocation(false)
        } else {
            // use position saved in preferences, fall back to default (Europe)
            val cameraUpdate =
                map.cameraUpdateFactory.newLatLngZoom(
                    prefs.currentMapLocation,
                    prefs.currentMapZoom
                )
            map.moveCamera(cameraUpdate)
        }

        mapSurfaceCallback.cameraMoveStartedListener = {
            if (myLocationEnabled) {
                myLocationEnabled = false
                myLocationNeedsUpdate = true
            }
        }

        mapSurfaceCallback.cameraIdleListener = {
            loadChargers()
            if (myLocationNeedsUpdate) {
                invalidate()
                myLocationNeedsUpdate = false
            }
        }
        loadChargers()
    }

    private fun enableLocation(animated: Boolean) {
        myLocationEnabled = true
        myLocationNeedsUpdate = true
        if (location != null) {
            val map = map ?: return
            val update = map.cameraUpdateFactory.newLatLngZoom(
                LatLng.fromLocation(location),
                13f
            )
            if (animated) {
                mapSurfaceCallback.animateCamera(update)
            } else {
                map.moveCamera(update)
            }
        }
    }
}