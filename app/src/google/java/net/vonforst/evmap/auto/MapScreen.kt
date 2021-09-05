package net.vonforst.evmap.auto

import android.location.Location
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.car2go.maps.model.LatLng
import kotlinx.coroutines.*
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.FILTERS_CUSTOM
import net.vonforst.evmap.model.FILTERS_DISABLED
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.availabilityText
import net.vonforst.evmap.ui.getMarkerTint
import net.vonforst.evmap.utils.distanceBetween
import net.vonforst.evmap.viewmodel.filtersWithValue
import net.vonforst.evmap.viewmodel.getFilterValues
import net.vonforst.evmap.viewmodel.getFilters
import net.vonforst.evmap.viewmodel.getReferenceData
import java.io.IOException
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.collections.set
import kotlin.math.roundToInt

/**
 * Main map screen showing either nearby chargers or favorites
 */
class MapScreen(ctx: CarContext, val session: EVMapSession, val favorites: Boolean = false) :
    Screen(ctx), LocationAwareScreen {
    private var updateCoroutine: Job? = null
    private var numUpdates = 0
    private val maxNumUpdates = 2

    private var location: Location? = null
    private var lastUpdateLocation: Location? = null
    private var chargers: List<ChargeLocation>? = null
    private var prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(carContext)
    private val api by lazy {
        createApi(prefs.dataSource, ctx)
    }
    private val searchRadius = 5 // kilometers
    private val updateThreshold = 2000 // meters
    private val availabilityUpdateThreshold = Duration.ofMinutes(1)
    private var availabilities: MutableMap<Long, Pair<ZonedDateTime, ChargeLocationStatus>> =
        HashMap()
    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
    } else 6

    private val referenceData = api.getReferenceData(lifecycleScope, carContext)
    private val filterStatus = MutableLiveData<Long>().apply {
        value = prefs.filterStatus.takeUnless { it == FILTERS_CUSTOM } ?: FILTERS_DISABLED
    }
    private val filterValues = db.filterValueDao().getFilterValues(filterStatus, prefs.dataSource)
    private val filters = api.getFilters(referenceData, carContext.stringProvider())
    private val filtersWithValue = filtersWithValue(filters, filterValues)

    init {
        filtersWithValue.observe(this) {
            loadChargers()
        }
    }

    override fun onGetTemplate(): Template {
        session.mapScreen = this
        return PlaceListMapTemplate.Builder().apply {
            setTitle(
                carContext.getString(
                    if (favorites) {
                        R.string.auto_favorites
                    } else {
                        R.string.auto_chargers_closeby
                    }
                )
            )
            location?.let {
                setAnchor(Place.Builder(CarLocation.create(it)).build())
            } ?: setLoading(true)
            chargers?.take(maxRows)?.let { chargerList ->
                val builder = ItemList.Builder()
                // only show the city if not all chargers are in the same city
                val showCity = chargerList.map { it.address.city }.distinct().size > 1
                chargerList.forEach { charger ->
                    builder.addItem(formatCharger(charger, showCity))
                }
                builder.setNoItemsMessage(
                    carContext.getString(
                        if (favorites) {
                            R.string.auto_no_favorites_found
                        } else {
                            R.string.auto_no_chargers_found
                        }
                    )
                )
                setItemList(builder.build())
            } ?: setLoading(true)
            setCurrentLocationEnabled(true)
            setHeaderAction(Action.BACK)
            if (!favorites) {
                val filtersCount = filtersWithValue.value?.count {
                    !it.value.hasSameValueAs(it.filter.defaultValue())
                }

                setActionStrip(
                    ActionStrip.Builder()
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
                            screenManager.pushForResult(FilterScreen(carContext)) {
                                chargers = null
                                numUpdates = 0
                                filterStatus.value = prefs.filterStatus
                            }
                            session.mapScreen = null
                        }
                        .build())
                    .build())
            }
            build()
        }.build()
    }

    private fun formatCharger(charger: ChargeLocation, showCity: Boolean): Row {
        val color = ContextCompat.getColor(carContext, getMarkerTint(charger))
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
            if (showCity && charger.address.city != null && charger.address.city !in charger.name) {
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
                val distance = distanceBetween(
                    it.latitude, it.longitude,
                    charger.coordinates.lat, charger.coordinates.lng
                ) / 1000
                text.append(
                    "distance",
                    DistanceSpan.create(Distance.create(distance, Distance.UNIT_KILOMETERS)),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // power
            if (text.isNotEmpty()) text.append(" · ")
            text.append("${charger.maxPower.roundToInt()} kW")

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

        invalidate()

        if (lastUpdateLocation == null ||
            location.distanceTo(lastUpdateLocation) > updateThreshold
        ) {
            lastUpdateLocation = location
            // update displayed chargers
            loadChargers()
        }
    }

    private fun loadChargers() {
        val location = location ?: return
        val referenceData = referenceData.value ?: return
        val filters = filtersWithValue.value ?: return

        numUpdates++
        println(numUpdates)
        if (numUpdates > maxNumUpdates) {
            CarToast.makeText(carContext, R.string.auto_no_refresh_possible, CarToast.LENGTH_LONG)
                .show()
            return
        }
        updateCoroutine = lifecycleScope.launch {
            try {
                // load chargers
                if (favorites) {
                    chargers = db.chargeLocationsDao().getAllChargeLocationsAsync().sortedBy {
                        distanceBetween(
                            location.latitude, location.longitude,
                            it.coordinates.lat, it.coordinates.lng
                        )
                    }
                } else {
                    val response = api.getChargepointsRadius(
                        referenceData,
                        LatLng.fromLocation(location),
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
                                LatLng.fromLocation(location),
                                searchRadius * 10,
                                zoom = 16f,
                                filters
                            )
                            chargers =
                                response.data?.filterIsInstance(ChargeLocation::class.java)
                        }
                    }
                }

                // remove outdated availabilities
                availabilities = availabilities.filter {
                    Duration.between(
                        it.value.first,
                        ZonedDateTime.now()
                    ) > availabilityUpdateThreshold
                }.toMutableMap()

                // update availabilities
                chargers?.take(maxRows)?.map {
                    lifecycleScope.async {
                        // update only if not yet stored
                        if (!availabilities.containsKey(it.id)) {
                            val date = ZonedDateTime.now()
                            val availability = getAvailability(it).data
                            if (availability != null) {
                                availabilities[it.id] = date to availability
                            }
                        }
                    }
                }?.awaitAll()

                updateCoroutine = null
                invalidate()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    CarToast.makeText(carContext, R.string.connection_error, CarToast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}