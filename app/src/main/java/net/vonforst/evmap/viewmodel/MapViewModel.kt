package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.car2go.maps.AnyMap
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.distanceBetween
import net.vonforst.evmap.api.goingelectric.*
import net.vonforst.evmap.storage.*
import net.vonforst.evmap.ui.cluster
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

data class MapPosition(val bounds: LatLngBounds, val zoom: Float)

data class PlaceWithBounds(val latLng: LatLng, val viewport: LatLngBounds?)

internal fun getClusterDistance(zoom: Float): Int? {
    return when (zoom) {
        in 0.0..7.0 -> 100
        in 7.0..11.5 -> 75
        in 11.5..12.5 -> 60
        in 12.5..13.0 -> 45
        else -> null
    }
}

class MapViewModel(application: Application, geApiKey: String) : AndroidViewModel(application) {
    private var api = GoingElectricApi.create(geApiKey, context = application)
    private var db = AppDatabase.getInstance(application)
    private var prefs = PreferenceDataSource(application)
    private var chargepointLoader: Job? = null

    val bottomSheetState: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    val mapPosition: MutableLiveData<MapPosition> by lazy {
        MutableLiveData<MapPosition>()
    }
    private val filterValues: LiveData<List<FilterValue>> by lazy {
        MediatorLiveData<List<FilterValue>>().apply {
            var source: LiveData<List<FilterValue>>? = null
            addSource(filterStatus) { status ->
                source?.let { removeSource(it) }
                source = db.filterValueDao().getFilterValues(status)
                addSource(source!!) { result ->
                    value = result
                }
            }
        }
    }
    private val plugs: LiveData<List<Plug>> by lazy {
        PlugRepository(api, viewModelScope, db.plugDao(), prefs).getPlugs()
    }
    private val networks: LiveData<List<Network>> by lazy {
        NetworkRepository(api, viewModelScope, db.networkDao(), prefs).getNetworks()
    }
    private val chargeCards: LiveData<List<ChargeCard>> by lazy {
        ChargeCardRepository(api, viewModelScope, db.chargeCardDao(), prefs).getChargeCards()
    }
    private val filters = getFilters(application, plugs, networks, chargeCards)

    private val filtersWithValue: LiveData<FilterValues> by lazy {
        filtersWithValue(filters, filterValues)
    }

    val filterProfiles: LiveData<List<FilterProfile>> by lazy {
        db.filterProfileDao().getProfiles()
    }

    val chargeCardMap: LiveData<Map<Long, ChargeCard>> by lazy {
        MediatorLiveData<Map<Long, ChargeCard>>().apply {
            value = null
            addSource(chargeCards) {
                value = chargeCards.value?.map {
                    it.id to it
                }?.toMap()
            }
        }
    }

    val filtersCount: LiveData<Int> by lazy {
        MediatorLiveData<Int>().apply {
            value = 0
            addSource(filtersWithValue) { filtersWithValue ->
                value = filtersWithValue.count {
                    it.filter.defaultValue() != it.value
                }
            }
        }
    }
    val chargepoints: MediatorLiveData<Resource<List<ChargepointListItem>>> by lazy {
        MediatorLiveData<Resource<List<ChargepointListItem>>>()
            .apply {
                value = Resource.loading(emptyList())
                listOf(mapPosition, filtersWithValue).forEach {
                    addSource(it) {
                        reloadChargepoints()
                    }
                }
            }
    }
    val filteredConnectors: MutableLiveData<Set<String>> by lazy {
        MutableLiveData<Set<String>>()
    }
    val filteredChargeCards: MutableLiveData<Set<Long>> by lazy {
        MutableLiveData<Set<Long>>()
    }

    val chargerSparse: MutableLiveData<ChargeLocation> by lazy {
        MutableLiveData<ChargeLocation>()
    }
    val chargerDetails: MediatorLiveData<Resource<ChargeLocation>> by lazy {
        MediatorLiveData<Resource<ChargeLocation>>().apply {
            addSource(chargerSparse) { charger ->
                if (charger != null) {
                    loadChargerDetails(charger)
                } else {
                    value = null
                }
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
                value = if (loc != null && charger != null && myLocationEnabled.value == true) {
                    distanceBetween(
                        loc.latitude,
                        loc.longitude,
                        charger.coordinates.lat,
                        charger.coordinates.lng
                    ) / 1000
                } else null
            }
            addSource(chargerSparse, callback)
            addSource(location, callback)
            addSource(myLocationEnabled, callback)
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
    val myLocationEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }
    val layersMenuOpen: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().apply {
            value = false
        }
    }

    val favorites: LiveData<List<ChargeLocation>> by lazy {
        db.chargeLocationsDao().getAllChargeLocations()
    }

    val searchResult: MutableLiveData<PlaceWithBounds> by lazy {
        MutableLiveData<PlaceWithBounds>()
    }

    val mapType: MutableLiveData<AnyMap.Type> by lazy {
        MutableLiveData<AnyMap.Type>().apply {
            value = AnyMap.Type.NORMAL
        }
    }

    val mapTrafficEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().apply {
            value = false
        }
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

    fun reloadPrefs() {
        filterStatus.value = prefs.filterStatus
    }

    fun toggleFilters() {
        if (filterStatus.value == FILTERS_DISABLED) {
            filterStatus.value = prefs.lastFilterProfile
        } else {
            filterStatus.value = FILTERS_DISABLED
        }
    }

    suspend fun copyFiltersToCustom() {
        if (filterStatus.value == FILTERS_CUSTOM) return

        db.filterValueDao().deleteFilterValuesForProfile(FILTERS_CUSTOM)
        filterValues.value?.forEach {
            it.profile = FILTERS_CUSTOM
            db.filterValueDao().insert(it)
        }
    }

    fun setMapType(type: AnyMap.Type) {
        mapType.value = type
    }

    fun insertFavorite(charger: ChargeLocation) {
        viewModelScope.launch {
            db.chargeLocationsDao().insert(charger)
        }
    }

    fun deleteFavorite(charger: ChargeLocation) {
        viewModelScope.launch {
            db.chargeLocationsDao().delete(charger)
        }
    }

    fun reloadChargepoints() {
        val pos = mapPosition.value ?: return
        val filters = filtersWithValue.value ?: return
        loadChargepoints(pos, filters)
    }

    private fun loadChargepoints(
        mapPosition: MapPosition,
        filters: FilterValues
    ) {
        chargepointLoader?.cancel()

        chargepoints.value = Resource.loading(chargepoints.value?.data)
        filteredConnectors.value = null
        filteredChargeCards.value = null
        val bounds = mapPosition.bounds
        val zoom = mapPosition.zoom
        chargepointLoader = viewModelScope.launch {
            val result = getChargepointsWithFilters(bounds, zoom, filters)
            filteredConnectors.value = result.second
            filteredChargeCards.value = result.third
            chargepoints.value = result.first
        }
    }

    private suspend fun getChargepointsWithFilters(
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues
    ): Triple<Resource<List<ChargepointListItem>>, Set<String>?, Set<Long>?> {
        val freecharging = filters.getBooleanValue("freecharging")
        val freeparking = filters.getBooleanValue("freeparking")
        val open247 = filters.getBooleanValue("open_247")
        val barrierfree = filters.getBooleanValue("barrierfree")
        val excludeFaults = filters.getBooleanValue("exclude_faults")
        val minPower = filters.getSliderValue("min_power")
        val minConnectors = filters.getSliderValue("min_connectors")

        val connectorsVal = filters.getMultipleChoiceValue("connectors")
        if (connectorsVal.values.isEmpty() && !connectorsVal.all) {
            // no connectors chosen
            return Triple(Resource.success(emptyList()), null, null)
        }
        val connectors = formatMultipleChoice(connectorsVal)
        val filteredConnectors = if (connectorsVal.all) null else connectorsVal.values

        val chargeCardsVal = filters.getMultipleChoiceValue("chargecards")
        if (chargeCardsVal.values.isEmpty() && !chargeCardsVal.all) {
            // no chargeCards chosen
            return Triple(Resource.success(emptyList()), filteredConnectors, null)
        }
        val chargeCards = formatMultipleChoice(chargeCardsVal)
        val filteredChargeCards =
            if (chargeCardsVal.all) null else chargeCardsVal.values.map { it.toLong() }.toSet()

        val networksVal = filters.getMultipleChoiceValue("networks")
        if (networksVal.values.isEmpty() && !networksVal.all) {
            // no networks chosen
            return Triple(Resource.success(emptyList()), filteredConnectors, filteredChargeCards)
        }
        val networks = formatMultipleChoice(networksVal)

        val categoriesVal = filters.getMultipleChoiceValue("categories")
        if (categoriesVal.values.isEmpty() && !categoriesVal.all) {
            // no categories chosen
            return Triple(Resource.success(emptyList()), filteredConnectors, filteredChargeCards)
        }
        val categories = formatMultipleChoice(categoriesVal)

        // do not use clustering if filters need to be applied locally.
        val useClustering = zoom < 13
        val geClusteringAvailable = minConnectors <= 1
        val useGeClustering = useClustering && geClusteringAvailable
        val clusterDistance = if (useClustering) getClusterDistance(zoom) else null

        var startkey: Int? = null
        val data = mutableListOf<ChargepointListItem>()
        do {
            // load all pages of the response
            try {
                val response = api.getChargepoints(
                    bounds.southwest.latitude,
                    bounds.southwest.longitude,
                    bounds.northeast.latitude,
                    bounds.northeast.longitude,
                    clustering = useGeClustering,
                    zoom = zoom,
                    clusterDistance = clusterDistance,
                    freecharging = freecharging,
                    minPower = minPower,
                    freeparking = freeparking,
                    open247 = open247,
                    barrierfree = barrierfree,
                    excludeFaults = excludeFaults,
                    plugs = connectors,
                    chargecards = chargeCards,
                    networks = networks,
                    categories = categories,
                    startkey = startkey
                )
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    return Triple(
                        Resource.error(response.message(), chargepoints.value?.data),
                        null,
                        null
                    )
                } else {
                    val body = response.body()!!
                    data.addAll(body.chargelocations)
                    startkey = body.startkey
                }
            } catch (e: IOException) {
                return Triple(
                    Resource.error(e.message, chargepoints.value?.data),
                    filteredConnectors,
                    filteredChargeCards
                )
            }
        } while (startkey != null && startkey < 10000)

        var result = data.filter { it ->
            // apply filters which GoingElectric does not support natively
            if (it is ChargeLocation) {
                it.chargepoints
                    .filter { it.power >= minPower }
                    .filter { if (!connectorsVal.all) it.type in connectorsVal.values else true }
                    .sumBy { it.count } >= minConnectors
            } else {
                true
            }
        }
        if (!geClusteringAvailable && useClustering) {
            // apply local clustering if server side clustering is not available
            Dispatchers.IO.run {
                result = cluster(result, zoom, clusterDistance!!)
            }
        }

        return Triple(Resource.success(result), filteredConnectors, filteredChargeCards)
    }

    private fun formatMultipleChoice(connectorsVal: MultipleChoiceFilterValue) =
        if (connectorsVal.all) null else connectorsVal.values.joinToString(",")

    private suspend fun loadAvailability(charger: ChargeLocation) {
        availability.value = Resource.loading(null)
        availability.value = getAvailability(charger)
    }

    private fun loadChargerDetails(charger: ChargeLocation) {
        chargerDetails.value = Resource.loading(null)
        api.getChargepointDetail(charger.id).enqueue(object :
            Callback<ChargepointList> {
            override fun onFailure(call: Call<ChargepointList>, t: Throwable) {
                chargerDetails.value = Resource.error(t.message, null)
                t.printStackTrace()
            }

            override fun onResponse(
                call: Call<ChargepointList>,
                response: Response<ChargepointList>
            ) {
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    chargerDetails.value = Resource.error(response.message(), null)
                } else {
                    chargerDetails.value =
                        Resource.success(response.body()!!.chargelocations[0] as ChargeLocation)
                }
            }
        })
    }
}