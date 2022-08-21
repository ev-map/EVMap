package net.vonforst.evmap.viewmodel

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.*
import com.car2go.maps.AnyMap
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.goingelectric.GEChargepoint
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OCMConnection
import net.vonforst.evmap.api.openchargemap.OCMReferenceData
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.autocomplete.PlaceWithBounds
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.FilterProfile
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.cluster
import net.vonforst.evmap.utils.distanceBetween
import java.io.IOException

@Parcelize
data class MapPosition(val bounds: LatLngBounds, val zoom: Float) : Parcelable

internal fun getClusterDistance(zoom: Float): Int? {
    return when (zoom) {
        in 0.0..7.0 -> 100
        in 7.0..11.0 -> 75
        else -> null
    }
}

class MapViewModel(application: Application, private val state: SavedStateHandle) :
    AndroidViewModel(application) {
    val apiType: Class<ChargepointApi<ReferenceData>>
        get() = api.value!!.javaClass
    val apiName: String
        get() = api.value!!.getName()

    private var db = AppDatabase.getInstance(application)
    private var prefs = PreferenceDataSource(application)
    private var api = MutableLiveData<ChargepointApi<ReferenceData>>().apply {
        value = createApi(prefs.dataSource, application)
    }

    val bottomSheetState: MutableLiveData<Int> by lazy {
        state.getLiveData("bottomSheetState")
    }

    val mapPosition: MutableLiveData<MapPosition> by lazy {
        state.getLiveData("mapPosition")
    }
    val filterStatus: MutableLiveData<Long> by lazy {
        MutableLiveData<Long>().apply {
            value = prefs.filterStatus
            observeForever {
                prefs.filterStatus = it
                if (it != FILTERS_DISABLED) prefs.lastFilterProfile = it
            }
        }
    }
    private val filterValues: LiveData<List<FilterValue>> =
        db.filterValueDao().getFilterValues(filterStatus, prefs.dataSource)
    private val referenceData =
        Transformations.switchMap(api) { it.getReferenceData(viewModelScope, application) }
    private val filters = Transformations.map(referenceData) {
        api.value!!.getFilters(
            it,
            application.stringProvider()
        )
    }

    private val filtersWithValue: LiveData<FilterValues> by lazy {
        filtersWithValue(filters, filterValues)
    }

    val filterProfiles: LiveData<List<FilterProfile>> by lazy {
        db.filterProfileDao().getProfiles(prefs.dataSource)
    }

    val chargeCardMap: LiveData<Map<Long, ChargeCard>> by lazy {
        MediatorLiveData<Map<Long, ChargeCard>>().apply {
            value = null
            addSource(referenceData) { data ->
                value = if (data is GEReferenceData) {
                    data.chargecards.map {
                        it.id to it.convert()
                    }.toMap()
                } else {
                    null
                }
            }
        }
    }

    val filtersCount: LiveData<Int> by lazy {
        MediatorLiveData<Int>().apply {
            value = 0
            addSource(filtersWithValue) { filtersWithValue ->
                value = filtersWithValue.count {
                    !it.value.hasSameValueAs(it.filter.defaultValue())
                }
            }
        }
    }
    val chargepoints: MediatorLiveData<Resource<List<ChargepointListItem>>> by lazy {
        MediatorLiveData<Resource<List<ChargepointListItem>>>()
            .apply {
                value = Resource.loading(emptyList())
                // this is not automatically updated with mapPosition, as we only want to update
                // when map is idle.
                listOf(filtersWithValue, referenceData).forEach {
                    addSource(it) {
                        reloadChargepoints()
                    }
                }
            }
    }
    val filteredConnectors: MutableLiveData<Set<String>> by lazy {
        MutableLiveData<Set<String>>()
    }
    val filteredMinPower: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }
    val filteredChargeCards: MutableLiveData<Set<Long>> by lazy {
        MutableLiveData<Set<Long>>()
    }

    val chargerSparse: MutableLiveData<ChargeLocation> by lazy {
        state.getLiveData("chargerSparse")
    }
    val chargerDetails: MediatorLiveData<Resource<ChargeLocation>> by lazy {
        MediatorLiveData<Resource<ChargeLocation>>().apply {
            value = state["chargerDetails"]
            listOf(chargerSparse, referenceData).forEach {
                addSource(it) { _ ->
                    val charger = chargerSparse.value
                    val refData = referenceData.value
                    if (charger != null && refData != null) {
                        if (charger.id != value?.data?.id) {
                            loadChargerDetails(charger, refData)
                        }
                    } else {
                        value = null
                    }
                }
            }
            observeForever {
                // persist data in case fragment gets recreated
                state["chargerDetails"] = it
            }
        }
    }
    val charger: MediatorLiveData<Resource<ChargeLocation>> by lazy {
        MediatorLiveData<Resource<ChargeLocation>>().apply {
            addSource(chargerDetails) {
                value = when (it?.status) {
                    null -> null
                    Status.SUCCESS -> Resource.success(it.data)
                    Status.LOADING -> Resource.loading(chargerSparse.value)
                    Status.ERROR -> Resource.error(it.message, chargerSparse.value)
                }
            }
        }
    }
    val chargerDistance: MediatorLiveData<Double> by lazy {
        MediatorLiveData<Double>().apply {
            val callback = { _: Any? ->
                val loc = location.value
                val charger = chargerSparse.value
                value = if (loc != null && charger != null) {
                    distanceBetween(
                        loc.latitude,
                        loc.longitude,
                        charger.coordinates.lat,
                        charger.coordinates.lng
                    )
                } else null
            }
            addSource(chargerSparse, callback)
            addSource(location, callback)
        }
    }
    val location: MutableLiveData<LatLng> by lazy {
        MutableLiveData<LatLng>()
    }
    val availability: MediatorLiveData<Resource<ChargeLocationStatus>> by lazy {
        MediatorLiveData<Resource<ChargeLocationStatus>>().apply {
            addSource(chargerSparse) { charger ->
                if (charger != null) {
                    viewModelScope.launch {
                        loadAvailability(charger)
                    }
                } else {
                    value = null
                }
            }
        }
    }
    val filteredAvailability: MediatorLiveData<Resource<ChargeLocationStatus>> by lazy {
        MediatorLiveData<Resource<ChargeLocationStatus>>().apply {
            val callback = { _: Any? ->
                val av = availability.value
                val filters = filtersWithValue.value
                if (av?.status == Status.SUCCESS && filters != null) {
                    value = Resource.success(
                        av.data!!.applyFilters(
                            filteredConnectors.value,
                            filteredMinPower.value
                        )
                    )
                } else {
                    value = av
                }
            }
            addSource(availability, callback)
            addSource(filteredConnectors, callback)
            addSource(filteredMinPower, callback)
        }
    }
    val myLocationEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }
    val layersMenuOpen: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().apply {
            value = false
        }
    }

    val favorites: LiveData<List<FavoriteWithDetail>> by lazy {
        db.favoritesDao().getAllFavorites()
    }

    val searchResult: MutableLiveData<PlaceWithBounds> by lazy {
        state.getLiveData("searchResult")
    }

    val mapType: MutableLiveData<AnyMap.Type> by lazy {
        MutableLiveData<AnyMap.Type>().apply {
            value = prefs.mapType
            observeForever {
                prefs.mapType = it
            }
        }
    }

    val mapTrafficEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().apply {
            value = prefs.mapTrafficEnabled
            observeForever {
                prefs.mapTrafficEnabled = it
            }
        }
    }

    fun reloadPrefs() {
        filterStatus.value = prefs.filterStatus
        api.value = createApi(prefs.dataSource, getApplication())
    }

    fun toggleFilters() {
        if (filterStatus.value == FILTERS_DISABLED) {
            filterStatus.value = prefs.lastFilterProfile
        } else {
            filterStatus.value = FILTERS_DISABLED
        }
    }

    suspend fun copyFiltersToCustom() {
        filterStatus.value?.let {
            withContext(Dispatchers.IO) {
                db.filterValueDao().copyFiltersToCustom(it, prefs.dataSource)
            }
        }
    }

    fun setMapType(type: AnyMap.Type) {
        mapType.value = type
    }

    fun insertFavorite(charger: ChargeLocation) {
        viewModelScope.launch {
            db.chargeLocationsDao().insert(charger)
            db.favoritesDao()
                .insert(Favorite(chargerId = charger.id, chargerDataSource = charger.dataSource))
        }
    }

    fun deleteFavorite(favorite: Favorite) {
        viewModelScope.launch {
            db.favoritesDao().delete(favorite)
        }
    }

    fun reloadChargepoints() {
        val pos = mapPosition.value ?: return
        val filters = filtersWithValue.value ?: return
        val referenceData = referenceData.value ?: return
        chargepointLoader(Triple(pos, filters, referenceData))
    }

    private val miniMarkerThreshold = 13f
    private val clusterThreshold = 11f
    val useMiniMarkers: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        for (source in listOf(filteredMinPower, mapPosition)) {
            addSource(source) {
                val minPower = filteredMinPower.value ?: 0
                val zoom = mapPosition.value?.zoom
                value = when {
                    zoom == null -> {
                        false
                    }
                    minPower >= 100 -> {
                        // when only showing high-power chargers we can use large markers
                        // because the density is much lower
                        false
                    }
                    else -> {
                        zoom < miniMarkerThreshold
                    }
                }
            }
        }
    }.distinctUntilChanged()

    private var chargepointLoader =
        throttleLatest(
            500L,
            viewModelScope
        ) { data: Triple<MapPosition, FilterValues, ReferenceData> ->
            chargepoints.value = Resource.loading(chargepoints.value?.data)

            val mapPosition = data.first
            val filters = data.second
            val api = api.value!!
            val refData = data.third

            if (filterStatus.value == FILTERS_FAVORITES) {
                // load favorites from local DB
                val b = mapPosition.bounds
                var chargers = db.favoritesDao().getFavoritesInBoundsAsync(
                    b.southwest.latitude,
                    b.northeast.latitude,
                    b.southwest.longitude,
                    b.northeast.longitude
                ).map { it.charger } as List<ChargepointListItem>

                val clusterDistance = getClusterDistance(mapPosition.zoom)
                clusterDistance?.let {
                    chargers = cluster(chargers, mapPosition.zoom, clusterDistance)
                }
                filteredConnectors.value = null
                filteredMinPower.value = null
                filteredChargeCards.value = null
                chargepoints.value = Resource.success(chargers)
                return@throttleLatest
            }

            if (api is GoingElectricApiWrapper) {
                val chargeCardsVal = filters.getMultipleChoiceValue("chargecards")!!
                filteredChargeCards.value =
                    if (chargeCardsVal.all) null else chargeCardsVal.values.map { it.toLong() }
                        .toSet()

                val connectorsVal = filters.getMultipleChoiceValue("connectors")!!
                filteredConnectors.value =
                    if (connectorsVal.all) null else connectorsVal.values.map {
                        GEChargepoint.convertTypeFromGE(it)
                    }.toSet()
                filteredMinPower.value = filters.getSliderValue("min_power")
            } else if (api is OpenChargeMapApiWrapper) {
                val connectorsVal = filters.getMultipleChoiceValue("connectors")!!
                filteredConnectors.value =
                    if (connectorsVal.all) null else connectorsVal.values.map {
                        OCMConnection.convertConnectionTypeFromOCM(
                            it.toLong(),
                            refData as OCMReferenceData
                        )
                    }.toSet()
                filteredMinPower.value = filters.getSliderValue("min_power")
            } else {
                filteredConnectors.value = null
                filteredMinPower.value = null
                filteredChargeCards.value = null
            }

            var result = api.getChargepoints(refData, mapPosition.bounds, mapPosition.zoom, filters)
            if (result.status == Status.ERROR && result.data == null) {
                // keep old results if new data could not be loaded
                result = Resource.error(result.message, chargepoints.value?.data)
            }

            chargepoints.value = result
        }

    private suspend fun loadAvailability(charger: ChargeLocation) {
        availability.value = Resource.loading(null)
        availability.value = getAvailability(charger)
    }

    fun reloadAvailability() {
        val charger = chargerSparse.value ?: return
        viewModelScope.launch {
            loadAvailability(charger)
        }
    }

    private var chargerLoadingTask: Job? = null

    private fun loadChargerDetails(charger: ChargeLocation, referenceData: ReferenceData) {
        chargerDetails.value = Resource.loading(null)
        chargerLoadingTask?.cancel()
        chargerLoadingTask = viewModelScope.launch {
            try {
                val chargerDetail = api.value!!.getChargepointDetail(referenceData, charger.id)
                chargerDetails.value = chargerDetail
                if (favorites.value?.any { it.charger.id == chargerDetail.data?.id } == true) {
                    // update data of stored favorite
                    db.chargeLocationsDao().insert(charger)
                }
            } catch (e: IOException) {
                chargerDetails.value = Resource.error(e.message, null)
                e.printStackTrace()
            }
        }
    }

    fun loadChargerById(chargerId: Long) {
        chargerDetails.value = Resource.loading(null)
        chargerSparse.value = null
        referenceData.observeForever(object : Observer<ReferenceData> {
            override fun onChanged(refData: ReferenceData) {
                referenceData.removeObserver(this)
                viewModelScope.launch {
                    val response = api.value!!.getChargepointDetail(refData, chargerId)
                    chargerDetails.value = response
                    if (response.status == Status.SUCCESS) {
                        chargerSparse.value = response.data

                        if (response.data != null && favorites.value?.any { it.charger.id == response.data.id } == true) {
                            // update data of stored favorite
                            db.chargeLocationsDao().insert(response.data)
                        }
                    } else {
                        chargerSparse.value = null
                    }
                }
            }
        })
    }
}