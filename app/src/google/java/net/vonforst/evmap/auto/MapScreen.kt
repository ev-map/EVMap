package net.vonforst.evmap.auto

import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.CarSensors
import androidx.car.app.hardware.info.Compass
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.car2go.maps.model.LatLng
import kotlinx.coroutines.*
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.FILTERS_FAVORITES
import net.vonforst.evmap.model.FilterValue
import net.vonforst.evmap.model.FilterWithValue
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.ChargeLocationsRepository
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.utils.bearingBetween
import net.vonforst.evmap.utils.distanceBetween
import net.vonforst.evmap.utils.headingDiff
import net.vonforst.evmap.viewmodel.Status
import net.vonforst.evmap.viewmodel.awaitFinished
import net.vonforst.evmap.viewmodel.filtersWithValue
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Main map screen showing either nearby chargers or favorites
 */
@androidx.car.app.annotations.ExperimentalCarApi
class MapScreen(ctx: CarContext, val session: EVMapSession) :
    Screen(ctx), LocationAwareScreen, OnContentRefreshListener,
    ItemList.OnItemVisibilityChangedListener, DefaultLifecycleObserver {
    companion object {
        val MARKER = "map"
    }

    private var updateCoroutine: Job? = null
    private var availabilityUpdateCoroutine: Job? = null

    private var visibleStart: Int? = null
    private var visibleEnd: Int? = null

    private var location: Location? = null
    private var lastDistanceUpdateTime: Instant? = null
    private var lastChargersUpdateTime: Instant? = null
    private var chargers: List<ChargeLocation>? = null
    private var loadingError = false
    private var locationError = false
    private var prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(carContext)
    private val repo =
        ChargeLocationsRepository(createApi(prefs.dataSource, ctx), lifecycleScope, db, prefs)
    private val searchRadius = 5 // kilometers
    private val distanceUpdateThreshold = Duration.ofSeconds(15)
    private val availabilityUpdateThreshold = Duration.ofMinutes(1)
    private val chargersUpdateThresholdDistance = 500  // meters
    private val chargersUpdateThresholdTime = Duration.ofSeconds(30)
    private var availabilities: MutableMap<Long, Pair<ZonedDateTime, ChargeLocationStatus?>> =
        HashMap()
    private val maxRows =
        min(ctx.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST), 25)
    private val supportsRefresh = ctx.isAppDrivenRefreshSupported

    private var filterStatus = prefs.filterStatus
    private var filtersWithValue: List<FilterWithValue<FilterValue>>? = null

    private val carInfo: CarInfo by lazy {
        (ctx.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager).carInfo
    }
    private val carSensors: CarSensors by lazy { carContext.patchedCarSensors }
    private var energyLevel: EnergyLevel? = null
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

    private var searchLocation: LatLng? = null

    init {
        lifecycle.addObserver(this)
        marker = MARKER
    }

    override fun onGetTemplate(): Template {
        session.mapScreen = this
        return PlaceListMapTemplate.Builder().apply {
            setTitle(
                prefs.placeSearchResultAndroidAutoName?.let {
                    carContext.getString(R.string.auto_chargers_near_location, it)
                } ?: carContext.getString(
                    if (filterStatus == FILTERS_FAVORITES) {
                        R.string.auto_favorites
                    } else {
                        R.string.auto_chargers_closeby
                    }
                )
            )
            if (prefs.placeSearchResultAndroidAutoName != null) {
                searchLocation?.let {
                    setAnchor(Place.Builder(CarLocation.create(it.latitude, it.longitude)).apply {
                        if (prefs.placeSearchResultAndroidAutoName != null) {
                            setMarker(
                                PlaceMarker.Builder()
                                    .setColor(CarColor.PRIMARY)
                                    .build()
                            )
                        }
                    }.build())
                }
            } else {
                location?.let {
                    setAnchor(Place.Builder(CarLocation.create(it.latitude, it.longitude)).build())
                }
            }
            chargers?.take(maxRows)?.let { chargerList ->
                val builder = ItemList.Builder()
                // only show the city if not all chargers are in the same city
                val showCity = chargerList.map { it.address?.city }.distinct().size > 1
                chargerList.forEach { charger ->
                    builder.addItem(formatCharger(charger, showCity))
                }
                builder.setNoItemsMessage(
                    carContext.getString(
                        if (filterStatus == FILTERS_FAVORITES) {
                            R.string.auto_no_favorites_found
                        } else {
                            R.string.auto_no_chargers_found
                        }
                    )
                )
                builder.setOnItemsVisibilityChangedListener(this@MapScreen)
                setItemList(builder.build())
            } ?: run {
                if (loadingError) {
                    val builder = ItemList.Builder()
                    builder.setNoItemsMessage(
                        carContext.getString(R.string.connection_error)
                    )
                    setItemList(builder.build())
                } else if (locationError) {
                    val builder = ItemList.Builder()
                    builder.setNoItemsMessage(
                        carContext.getString(R.string.location_error)
                    )
                    setItemList(builder.build())
                } else {
                    setLoading(true)
                }
            }
            setCurrentLocationEnabled(true)
            setHeaderAction(Action.APP_ICON)
            val filtersCount = if (filterStatus == FILTERS_FAVORITES) 1 else {
                filtersWithValue?.count {
                    !it.value.hasSameValueAs(it.filter.defaultValue())
                }
            }

            setActionStrip(
                ActionStrip.Builder()
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
                                if (!supportsRefresh) {
                                    screenManager.pushForResult(DummyReturnScreen(carContext)) {
                                        chargers = null
                                        loadChargers()
                                    }
                                } else {
                                    chargers = null
                                    loadChargers()
                                }
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
                    .build())
            if (carContext.carAppApiLevel >= 5 ||
                (BuildConfig.FLAVOR_automotive == "automotive" && carContext.carAppApiLevel >= 4)
            ) {
                setOnContentRefreshListener(this@MapScreen)
            }
        }.build()
    }

    private fun formatCharger(charger: ChargeLocation, showCity: Boolean): Row {
        val markerTint = if ((charger.maxPower ?: 0.0) > 100) {
            R.color.charger_100kw_dark  // slightly darker color for better contrast
        } else {
            getMarkerTint(charger)
        }
        val color = ContextCompat.getColor(carContext, markerTint)
        val place =
            Place.Builder(CarLocation.create(charger.coordinates.lat, charger.coordinates.lng))
                .setMarker(
                    PlaceMarker.Builder()
                        .setColor(CarColor.createCustom(color, color))
                        .build()
                )
                .build()

        return Row.Builder().apply {
            // only show the city if not all chargers are in the same city (-> showCity == true)
            // and the city is not already contained in the charger name
            if (showCity && charger.address?.city != null && charger.address.city !in charger.name) {
                setTitle(
                    CarText.Builder("${charger.name} · ${charger.address.city}")
                    .addVariant(charger.name)
                    .build())
            } else {
                setTitle(charger.name)
            }

            val text = SpannableStringBuilder()

            // distance
            location?.let {
                val distanceMeters = distanceBetween(
                    it.latitude, it.longitude,
                    charger.coordinates.lat, charger.coordinates.lng
                )
                text.append(
                    "distance",
                    DistanceSpan.create(
                        roundValueToDistance(
                            distanceMeters,
                            energyLevel?.distanceDisplayUnit?.value
                        )
                    ),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // power
            val power = charger.maxPower;
            if (power != null) {
                if (text.isNotEmpty()) text.append(" · ")
                text.append("${power.roundToInt()} kW")
            }

            // availability
            availabilities[charger.id]?.second?.let { av ->
                val status = av.status.values.flatten()
                val available = availabilityText(status)
                val total = charger.chargepoints.sumBy { it.count }

                if (text.isNotEmpty()) text.append(" · ")
                text.append(
                    "$available/$total",
                    ForegroundCarColorSpan.create(carAvailabilityColor(status)),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            addText(text)
            setMetadata(
                Metadata.Builder()
                    .setPlace(place)
                    .build()
            )

            setOnClickListener {
                screenManager.push(ChargerDetailScreen(carContext, charger))
                session.mapScreen = null
            }
        }.build()
    }

    override fun updateLocation(location: Location) {
        if (location.latitude == this.location?.latitude
            && location.longitude == this.location?.longitude
        ) {
            return
        }
        val previousLocation = this.location
        this.location = location
        if (previousLocation == null) {
            loadChargers()
            return
        }

        val now = Instant.now()
        if (lastDistanceUpdateTime == null ||
            Duration.between(lastDistanceUpdateTime, now) > distanceUpdateThreshold
        ) {
            lastDistanceUpdateTime = now
            // update displayed distances
            invalidate()
        }

        // if chargers are searched around current location, consider app-driven refresh
        val searchLocation =
            if (prefs.placeSearchResultAndroidAuto == null) searchLocation else null
        val distance = searchLocation?.let {
            distanceBetween(
                it.latitude, it.longitude, location.latitude, location.longitude
            )
        } ?: 0.0
        if (supportsRefresh && (lastChargersUpdateTime == null ||
                    Duration.between(
                        lastChargersUpdateTime,
                        now
                    ) > chargersUpdateThresholdTime) && (distance > chargersUpdateThresholdDistance)
        ) {
            onContentRefreshRequested()
        }
    }

    private fun loadChargers() {
        val location = location ?: return

        val searchLocation =
            prefs.placeSearchResultAndroidAuto ?: LatLng.fromLocation(location)
        this.searchLocation = searchLocation

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
                    chargers =
                        db.favoritesDao().getAllFavoritesAsync().map { it.charger }.sortedBy {
                            distanceBetween(
                                location.latitude, location.longitude,
                                it.coordinates.lat, it.coordinates.lng
                            )
                        }
                } else {
                    // try multiple search radii until we have enough chargers
                    var chargers: List<ChargeLocation>? = null
                    for (radius in listOf(searchRadius, searchRadius * 10, searchRadius * 50)) {
                        val response = repo.getChargepointsRadius(
                            searchLocation,
                            radius,
                            zoom = 16f,
                            filtersWithValue
                        ).awaitFinished()
                        if (response.status == Status.ERROR) {
                            loadingError = true
                            this@MapScreen.chargers = null
                            invalidate()
                            return@launch
                        }
                        chargers = response.data?.filterIsInstance(ChargeLocation::class.java)
                        if (prefs.placeSearchResultAndroidAutoName == null) {
                            chargers = headingFilter(
                                chargers,
                                searchLocation
                            )
                        }
                        if (chargers == null || chargers.size >= maxRows) {
                            break
                        }
                    }
                    this@MapScreen.chargers = chargers
                }

                updateCoroutine = null
                lastChargersUpdateTime = Instant.now()
                lastDistanceUpdateTime = Instant.now()
                invalidate()
            } catch (e: IOException) {
                loadingError = true
                invalidate()
            }
        }
    }

    /**
     * Filters by heading if heading available and enabled
     */
    private fun headingFilter(
        chargers: List<ChargeLocation>?,
        searchLocation: LatLng
    ): List<ChargeLocation>? {
        // use compass heading if available, otherwise fall back to GPS
        val location = location
        val heading = heading?.orientations?.value?.get(0)
            ?: if (location?.hasBearing() == true) location.bearing else null
        return heading?.let { heading ->
            if (!prefs.showChargersAheadAndroidAuto) return@let chargers

            chargers?.filter {
                val bearing = bearingBetween(
                    searchLocation.latitude,
                    searchLocation.longitude,
                    it.coordinates.lat,
                    it.coordinates.lng
                )
                val diff = headingDiff(bearing, heading.toDouble())
                abs(diff) < 30
            }
        } ?: chargers
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
        setupListeners()
        session.requestLocationUpdates()
        locationError = false
        Handler(Looper.getMainLooper()).postDelayed({
            if (location == null) {
                locationError = true
                invalidate()
            }
        }, 5000)

        // Reloading chargers in onStart does not seem to count towards content limit.
        // So let's do this so the user gets fresh chargers when re-entering the app.
        if (prefs.dataSource != repo.api.value?.id) {
            repo.api.value = createApi(prefs.dataSource, carContext)
        }
        invalidate()
        loadChargers()
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
        removeListeners()
    }

    private fun removeListeners() {
        if (supportsCarApiLevel3(carContext)) {
            println("Removing energy level listener")
            carInfo.removeEnergyLevelListener(::onEnergyLevelUpdated)
            carSensors.removeCompassListener(::onCompassUpdated)
        }
    }

    override fun onContentRefreshRequested() {
        loadChargers()
        availabilities.clear()

        val start = visibleStart
        val end = visibleEnd
        if (start != null && end != null) {
            onItemVisibilityChanged(start, end)
        }
    }

    override fun onItemVisibilityChanged(startIndex: Int, endIndex: Int) {
        // when the list is scrolled, load corresponding availabilities
        if (startIndex == visibleStart && endIndex == visibleEnd && !availabilities.isEmpty()) return
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

            val chargers = chargers ?: return@launch
            if (chargers.isEmpty()) return@launch

            val tasks = chargers.subList(
                min(startIndex, chargers.size - 1),
                min(endIndex, chargers.size - 1)
            ).mapNotNull {
                // update only if not yet stored
                if (!availabilities.containsKey(it.id)) {
                    lifecycleScope.async {
                        val availability = getAvailability(it).data
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
}