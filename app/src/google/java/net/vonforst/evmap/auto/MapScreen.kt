package net.vonforst.evmap.auto

import android.content.pm.PackageManager
import android.location.Location
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.*
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
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.utils.distanceBetween
import net.vonforst.evmap.viewmodel.filtersWithValue
import net.vonforst.evmap.viewmodel.getFilterValues
import net.vonforst.evmap.viewmodel.getReferenceData
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.collections.set
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
    private var chargers: List<ChargeLocation>? = null
    private var prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(carContext)
    private val api by lazy {
        createApi(prefs.dataSource, ctx)
    }
    private val searchRadius = 5 // kilometers
    private val distanceUpdateThreshold = Duration.ofSeconds(15)
    private val availabilityUpdateThreshold = Duration.ofMinutes(1)
    private var availabilities: MutableMap<Long, Pair<ZonedDateTime, ChargeLocationStatus?>> =
        HashMap()
    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
    } else 6

    private val referenceData = api.getReferenceData(lifecycleScope, carContext)
    private val filterStatus = MutableLiveData<Long>().apply {
        value = prefs.filterStatus
    }
    private val filterValues = db.filterValueDao().getFilterValues(filterStatus, prefs.dataSource)
    private val filters =
        Transformations.map(referenceData) { api.getFilters(it, carContext.stringProvider()) }
    private val filtersWithValue = filtersWithValue(filters, filterValues)

    private val hardwareMan: CarHardwareManager by lazy {
        ctx.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
    }
    private var energyLevel: EnergyLevel? = null
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
        filtersWithValue.observe(this) {
            loadChargers()
        }
        marker = MARKER
    }

    override fun onGetTemplate(): Template {
        session.requestLocationUpdates()

        session.mapScreen = this
        return PlaceListMapTemplate.Builder().apply {
            setTitle(
                prefs.placeSearchResultAndroidAutoName?.let {
                    carContext.getString(R.string.auto_chargers_near_location, it)
                } ?: carContext.getString(
                    if (filterStatus.value == FILTERS_FAVORITES) {
                        R.string.auto_favorites
                    } else {
                        R.string.auto_chargers_closeby
                    }
                )
            )
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
            } ?: setLoading(true)
            chargers?.take(maxRows)?.let { chargerList ->
                val builder = ItemList.Builder()
                // only show the city if not all chargers are in the same city
                val showCity = chargerList.map { it.address?.city }.distinct().size > 1
                chargerList.forEach { charger ->
                    builder.addItem(formatCharger(charger, showCity))
                }
                builder.setNoItemsMessage(
                    carContext.getString(
                        if (filterStatus.value == FILTERS_FAVORITES) {
                            R.string.auto_no_favorites_found
                        } else {
                            R.string.auto_no_chargers_found
                        }
                    )
                )
                builder.setOnItemsVisibilityChangedListener(this@MapScreen)
                setItemList(builder.build())
            } ?: setLoading(true)
            setCurrentLocationEnabled(true)
            setHeaderAction(Action.APP_ICON)
            val filtersCount = if (filterStatus.value == FILTERS_FAVORITES) 1 else {
                filtersWithValue.value?.count {
                    !it.value.hasSameValueAs(it.filter.defaultValue())
                }
            }

            setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder()
                        .setIcon(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_settings
                                )
                            ).setTint(CarColor.DEFAULT).build()
                        )
                        .setOnClickListener {
                            screenManager.push(SettingsScreen(carContext))
                            session.mapScreen = null
                        }
                        .build())
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
                                screenManager.pushForResult(FilterScreen(carContext, session)) {
                                    chargers = null
                                    filterStatus.value = prefs.filterStatus
                                }
                                session.mapScreen = null
                            }
                            .build())
                    .build())
            setOnContentRefreshListener(this@MapScreen)
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
                screenManager.pushForResult(ChargerDetailScreen(carContext, charger)) {
                    if (filterStatus.value == FILTERS_FAVORITES) {
                        // favorites list may have been updated
                        chargers = null
                        loadChargers()
                    }
                }
            }
        }.build()
    }

    override fun updateLocation(location: Location) {
        if (location.latitude == this.location?.latitude
            && location.longitude == this.location?.longitude
        ) {
            return
        }
        this.location = location
        if (updateCoroutine != null) {
            // don't update while still loading last update
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
    }

    private fun loadChargers() {
        val location = location ?: return
        val referenceData = referenceData.value ?: return
        val filters = filtersWithValue.value ?: return

        val searchLocation =
            prefs.placeSearchResultAndroidAuto ?: LatLng.fromLocation(location)
        this.searchLocation = searchLocation

        updateCoroutine = lifecycleScope.launch {
            try {
                // load chargers
                if (filterStatus.value == FILTERS_FAVORITES) {
                    chargers =
                        db.favoritesDao().getAllFavoritesAsync().map { it.charger }.sortedBy {
                            distanceBetween(
                                location.latitude, location.longitude,
                                it.coordinates.lat, it.coordinates.lng
                            )
                        }
                } else {
                    val response = api.getChargepointsRadius(
                        referenceData,
                        searchLocation,
                        searchRadius,
                        zoom = 16f,
                        filters
                    )
                    chargers = response.data?.filterIsInstance(ChargeLocation::class.java)
                    chargers?.let {
                        if (it.size < maxRows) {
                            // try again with larger radius
                            val response = api.getChargepointsRadius(
                                referenceData,
                                searchLocation,
                                searchRadius * 10,
                                zoom = 16f,
                                filters
                            )
                            chargers =
                                response.data?.filterIsInstance(ChargeLocation::class.java)
                        }
                    }
                }

                updateCoroutine = null
                lastDistanceUpdateTime = Instant.now()
                invalidate()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    CarToast.makeText(carContext, R.string.connection_error, CarToast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun onEnergyLevelUpdated(energyLevel: EnergyLevel) {
        val isUpdate = this.energyLevel == null
        this.energyLevel = energyLevel
        if (isUpdate) invalidate()
    }

    override fun onResume(owner: LifecycleOwner) {
        setupListeners()
    }

    private fun setupListeners() {
        if (!permissions.all {
                ContextCompat.checkSelfPermission(
                    carContext,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            })
            return

        if (supportsCarApiLevel3(carContext)) {
            println("Setting up energy level listener")
            val exec = ContextCompat.getMainExecutor(carContext)
            hardwareMan.carInfo.addEnergyLevelListener(exec, ::onEnergyLevelUpdated)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        removeListeners()
    }

    private fun removeListeners() {
        if (supportsCarApiLevel3(carContext)) {
            println("Removing energy level listener")
            hardwareMan.carInfo.removeEnergyLevelListener(::onEnergyLevelUpdated)
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