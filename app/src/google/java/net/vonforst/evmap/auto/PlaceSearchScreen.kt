package net.vonforst.evmap.auto

import android.content.pm.PackageManager
import android.location.Location
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
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
import net.vonforst.evmap.adapter.iconForPlaceType
import net.vonforst.evmap.adapter.isSpecialPlace
import net.vonforst.evmap.autocomplete.*
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.storage.RecentAutocompletePlace
import java.io.IOException
import java.time.Instant

@ExperimentalCarApi
class PlaceSearchScreen(ctx: CarContext, val session: EVMapSession) : Screen(ctx),
    SearchTemplate.SearchCallback, LocationAwareScreen,
    DefaultLifecycleObserver {
    private val hardwareMan: CarHardwareManager by lazy {
        ctx.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
    }
    private var resultList: List<AutocompletePlace>? = null
    private var recentResults = mutableListOf<RecentAutocompletePlace>()
    private var currentProvider: AutocompleteProvider? = null
    private val providers = getAutocompleteProviders(ctx)
    private val recents = AppDatabase.getInstance(ctx).recentAutocompletePlaceDao()
    private val maxItems = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else 6
    private var location: Location? = null
    private var energyLevel: EnergyLevel? = null
    private var updateJob: Job? = null
    private val prefs = PreferenceDataSource(ctx)

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

    init {
        lifecycle.addObserver(this)
        update("")
    }

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(this).apply {
            setHeaderAction(Action.BACK)
            setSearchHint(carContext.getString(R.string.search))
            resultList?.let {
                setItemList(buildItemList(it))
            } ?: setLoading(true)
        }.build()
    }

    private fun buildItemList(results: List<AutocompletePlace>): ItemList {
        return ItemList.Builder().apply {
            results.forEach { place ->
                addItem(Row.Builder().apply {
                    setTitle(place.primaryText)
                    addText(place.secondaryText)

                    val icon = iconForPlaceType(place.types)
                    setImage(
                        CarIcon.Builder(IconCompat.createWithResource(carContext, icon))
                            .setTint(if (isSpecialPlace(place.types)) CarColor.PRIMARY else CarColor.DEFAULT)
                            .build()
                    )

                    // distance
                    place.distanceMeters?.let {
                        val text = SpannableStringBuilder()
                        text.append(
                            "distance",
                            DistanceSpan.create(
                                roundValueToDistance(
                                    it,
                                    energyLevel?.distanceDisplayUnit?.value
                                )
                            ),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        addText(text)
                    }

                    setOnClickListener {
                        lifecycleScope.launch {
                            val placeDetails = getDetails(place.id)
                            prefs.placeSearchResultAndroidAuto = placeDetails.latLng
                            prefs.placeSearchResultAndroidAutoName =
                                place.primaryText.toString()
                            screenManager.popTo(MapScreen.MARKER)
                        }
                    }
                }.build())
            }
        }.build()
    }

    override fun onSearchTextChanged(searchText: String) {
        update(searchText)
    }

    override fun onSearchSubmitted(searchText: String) {
        update(searchText)
    }

    private fun update(searchText: String) {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            if (prefs.searchProvider == "mapbox" && !isShortQuery(searchText)) {
                delay(500L)
            }
            try {
                loadNewList(searchText)
            } catch (e: IOException) {
                CarToast.makeText(
                    carContext,
                    R.string.autocomplete_connection_error,
                    CarToast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun loadNewList(query: String) {
        for (provider in providers) {
            try {
                recentResults.clear()
                currentProvider = provider

                // first search in recent places
                val recentPlaces = if (query.isEmpty()) {
                    recents.getAllAsync(provider.id, limit = maxItems)
                } else {
                    recents.searchAsync(query, provider.id, limit = maxItems)
                }
                recentResults.addAll(recentPlaces)
                resultList =
                    recentPlaces.map { it.asAutocompletePlace(LatLng.fromLocation(location)) }
                invalidate()

                // if we already have enough results or the query is short, stop here
                if (isShortQuery(query) || recentResults.size >= maxItems) break

                // then search online
                val recentIds = recentPlaces.map { it.id }
                resultList = withContext(Dispatchers.IO) {
                    (resultList!! + provider.autocomplete(query, LatLng.fromLocation(location))
                        .filter { !recentIds.contains(it.id) }).take(maxItems)
                }
                invalidate()
                break
            } catch (e: ApiUnavailableException) {
                e.printStackTrace()
            }
        }
    }

    private fun isShortQuery(query: CharSequence) = query.length < 3

    override fun updateLocation(location: Location) {
        this.location = location
    }

    override fun onResume(owner: LifecycleOwner) {
        session.requestLocationUpdates()
        session.mapScreen = this

        if (supportsCarApiLevel3(carContext) && permissions.all {
                ContextCompat.checkSelfPermission(
                    carContext,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }) {

            println("Setting up energy level listener")
            val exec = ContextCompat.getMainExecutor(carContext)
            hardwareMan.carInfo.addEnergyLevelListener(exec, ::onEnergyLevelUpdated)
        }
    }

    private fun onEnergyLevelUpdated(energyLevel: EnergyLevel) {
        val isUpdate = this.energyLevel == null
        this.energyLevel = energyLevel
        if (isUpdate) invalidate()
    }

    override fun onPause(owner: LifecycleOwner) {
        session.mapScreen = null

        if (supportsCarApiLevel3(carContext)) {
            hardwareMan.carInfo.removeEnergyLevelListener(::onEnergyLevelUpdated)
        }
    }

    suspend fun getDetails(id: String): PlaceWithBounds {
        val provider = currentProvider!!
        val result = resultList!!.find { it.id == id }!!

        val recentPlace = recentResults.find { it.id == id }
        if (recentPlace != null) return recentPlace.asPlaceWithBounds()

        val details = provider.getDetails(id)

        recents.insert(RecentAutocompletePlace(result, details, provider.id, Instant.now()))

        return details
    }
}
