package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.car2go.maps.AnyMap
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import kotlinx.coroutines.launch
import net.vonforst.evmap.R
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.FilterProfile
import net.vonforst.evmap.storage.GEReferenceDataRepository
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.utils.distanceBetween
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
    private var api: ChargepointApi<ReferenceData> = OpenChargeMapApiWrapper(
        application.getString(
            R.string.openchargemap_key
        )
    )
    val apiType: Class<ChargepointApi<ReferenceData>>
        get() = api.javaClass
    val apiName: String
        get() = api.getName()

    // = GoingElectricApiWrapper(geApiKey, context = application)
    private var db = AppDatabase.getInstance(application)
    private var prefs = PreferenceDataSource(application)

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
    private val referenceData: LiveData<out ReferenceData> by lazy {
        val api = api
        if (api is GoingElectricApiWrapper) {
            GEReferenceDataRepository(
                api,
                viewModelScope,
                db.geReferenceDataDao(),
                prefs
            ).getReferenceData()
        } else {
            // TODO: create repository
            MutableLiveData<ReferenceData>().apply {
                viewModelScope.launch {
                    val referenceData1 = api.getReferenceData()
                    if (referenceData1.status == Status.SUCCESS) {
                        value = referenceData1.data
                    }
                }
            }
        }
    }
    private val filters = MediatorLiveData<List<Filter<FilterValue>>>().apply {
        addSource(referenceData) { data ->
            val api = api
            value = api.getFilters(data, application.stringProvider())
        }
    }

    private val filtersWithValue: LiveData<FilterValues> by lazy {
        filtersWithValue(filters, filterValues)
    }

    val filterProfiles: LiveData<List<FilterProfile>> by lazy {
        db.filterProfileDao().getProfiles()
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
            listOf(chargerSparse, referenceData).forEach {
                addSource(it) { _ ->
                    val charger = chargerSparse.value
                    val refData = referenceData.value
                    if (charger != null && refData != null) {
                        loadChargerDetails(charger, refData)
                    } else {
                        value = null
                    }
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
                value = if (loc != null && charger != null) {
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
                    value = Resource.success(av.data!!.applyFilters(filters))
                } else {
                    value = av
                }
            }
            addSource(availability, callback)
            addSource(filtersWithValue, callback)
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
        val referenceData = referenceData.value ?: return
        chargepointLoader(Triple(pos, filters, referenceData))
    }

    private var chargepointLoader =
        throttleLatest(
            500L,
            viewModelScope
        ) { data: Triple<MapPosition, FilterValues, ReferenceData> ->
            chargepoints.value = Resource.loading(chargepoints.value?.data)
            filteredConnectors.value = null
            filteredChargeCards.value = null

            val mapPosition = data.first
            val filters = data.second
            val api = api
            val refData = data.third
            var result = api.getChargepoints(refData, mapPosition.bounds, mapPosition.zoom, filters)
            if (result.status == Status.ERROR && result.data == null) {
                // keep old results if new data could not be loaded
                result = Resource.error(result.message, chargepoints.value?.data)
            }
            chargepoints.value = result

            if (api is GoingElectricApiWrapper) {
                val chargeCardsVal = filters.getMultipleChoiceValue("chargecards")!!
                filteredChargeCards.value =
                    if (chargeCardsVal.all) null else chargeCardsVal.values.map { it.toLong() }
                        .toSet()

                val connectorsVal = filters.getMultipleChoiceValue("connectors")!!
                filteredConnectors.value = if (connectorsVal.all) null else connectorsVal.values
            }
        }

    private suspend fun loadAvailability(charger: ChargeLocation) {
        availability.value = Resource.loading(null)
        availability.value = getAvailability(charger)
    }

    private fun loadChargerDetails(charger: ChargeLocation, referenceData: ReferenceData) {
        chargerDetails.value = Resource.loading(null)
        viewModelScope.launch {
            try {
                chargerDetails.value = api.getChargepointDetail(referenceData, charger.id)
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
                    val response = api.getChargepointDetail(refData, chargerId)
                    chargerDetails.value = response
                    if (response.status == Status.SUCCESS) {
                        chargerSparse.value = response.data
                    } else {
                        chargerSparse.value = null
                    }
                }
            }
        })
    }
}