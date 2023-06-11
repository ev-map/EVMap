package net.vonforst.evmap.viewmodel

import android.app.Application
import android.graphics.Point
import android.os.Parcelable
import androidx.lifecycle.*
import com.car2go.maps.AnyMap
import com.car2go.maps.Projection
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.mahc.custombottomsheetbehavior.BottomSheetBehaviorGoogleMapsLike
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.AvailabilityDetectorException
import net.vonforst.evmap.api.availability.AvailabilityRepository
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.TeslaGraphQlApi
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.equivalentPlugTypes
import net.vonforst.evmap.api.fronyx.FronyxApi
import net.vonforst.evmap.api.fronyx.FronyxEvseIdResponse
import net.vonforst.evmap.api.fronyx.FronyxStatus
import net.vonforst.evmap.api.goingelectric.GEChargepoint
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.openchargemap.OCMConnection
import net.vonforst.evmap.api.openchargemap.OCMReferenceData
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.autocomplete.PlaceWithBounds
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.ChargeLocationsRepository
import net.vonforst.evmap.storage.EncryptedPreferenceDataStore
import net.vonforst.evmap.storage.FilterProfile
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.cluster
import net.vonforst.evmap.utils.distanceBetween
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

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
    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferenceDataSource(application)
    private val encryptedPrefs = EncryptedPreferenceDataStore(application)
    private val repo = ChargeLocationsRepository(
        createApi(prefs.dataSource, application),
        viewModelScope,
        db,
        prefs
    )
    private val availabilityRepo = AvailabilityRepository(application)
    var mapProjection: Projection? = null

    val apiId = repo.api.map { it.id }

    init {
        // necessary so that apiId is updated
        apiId.observeForever { }
    }

    val apiName = repo.api.map { it.name }

    val bottomSheetState: MutableLiveData<Int> by lazy {
        state.getLiveData("bottomSheetState")
    }

    val bottomSheetExpanded = MediatorLiveData<Boolean>().apply {
        addSource(bottomSheetState) {
            when (it) {
                BottomSheetBehaviorGoogleMapsLike.STATE_COLLAPSED,
                BottomSheetBehaviorGoogleMapsLike.STATE_HIDDEN -> {
                    value = false
                }
                BottomSheetBehaviorGoogleMapsLike.STATE_EXPANDED,
                BottomSheetBehaviorGoogleMapsLike.STATE_ANCHOR_POINT -> {
                    value = true
                }
            }
        }
    }.distinctUntilChanged()

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
    private val filterValues: LiveData<List<FilterValue>?> = repo.api.switchMap {
        db.filterValueDao().getFilterValues(filterStatus, prefs.dataSource)
    }
    private val filters = repo.getFilters(application.stringProvider())

    private val filtersWithValue: LiveData<FilterValues?> by lazy {
        filtersWithValue(filters, filterValues)
    }

    val filterProfiles: LiveData<List<FilterProfile>> = repo.api.switchMap {
        db.filterProfileDao().getProfiles(prefs.dataSource)
    }

    val chargeCardMap = repo.chargeCardMap

    val filtersCount: LiveData<Int> by lazy {
        MediatorLiveData<Int>().apply {
            value = 0
            addSource(filtersWithValue) { filtersWithValue ->
                value = filtersWithValue?.count {
                    !it.value.hasSameValueAs(it.filter.defaultValue())
                } ?: 0
            }
        }
    }
    val chargepoints: MediatorLiveData<Resource<List<ChargepointListItem>>> by lazy {
        MediatorLiveData<Resource<List<ChargepointListItem>>>()
            .apply {
                value = Resource.loading(emptyList())
                // this is not automatically updated with mapPosition, as we only want to update
                // when map is idle.
                listOf(filtersWithValue, repo.api).forEach {
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

    val chargerSparse: MutableLiveData<ChargeLocation?> by lazy {
        state.getLiveData("chargerSparse")
    }
    val chargerDetails: LiveData<Resource<ChargeLocation>> = chargerSparse.switchMap { charger ->
        charger?.id?.let {
            repo.getChargepointDetail(it)
        }
    }.apply {
        observeForever { chargerDetail ->
            // persist data in case fragment gets recreated
            state["chargerDetails"] = chargerDetail
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
    private val triggerAvailabilityRefresh = MutableLiveData<Boolean>(true)
    val availability: LiveData<Resource<ChargeLocationStatus>> by lazy {
        chargerSparse.switchMap { charger ->
            charger?.let {
                triggerAvailabilityRefresh.switchMap {
                    liveData {
                        emit(Resource.loading(null))
                        emit(availabilityRepo.getAvailability(charger))
                    }
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

    val teslaPricing = availability.map {
        it.data?.extraData as? TeslaGraphQlApi.Pricing
    }

    val predictionApi = FronyxApi(application.getString(R.string.fronyx_key))

    val prediction: LiveData<Resource<List<FronyxEvseIdResponse>>> by lazy {
        availability.switchMap { av ->
            if (!prefs.predictionEnabled) return@switchMap null

            av.data?.evseIds?.let { evseIds ->
                liveData {
                    emit(Resource.loading(null))

                    val charger = charger.value?.data ?: return@liveData
                    val allEvseIds =
                        evseIds.filterKeys {
                            FronyxApi.isChargepointSupported(charger, it) &&
                                    filteredConnectors.value?.let { filtered ->
                                        equivalentPlugTypes(
                                            it.type
                                        ).any { filtered.contains(it) }
                                    } ?: true
                        }.flatMap { it.value }
                    if (allEvseIds.isEmpty()) {
                        emit(Resource.success(emptyList()))
                        return@liveData
                    }
                    try {
                        val result = predictionApi.getPredictionsForEvseIds(allEvseIds)
                        if (result.size == allEvseIds.size) {
                            emit(Resource.success(result))
                        } else {
                            emit(Resource.error("not all EVSEIDs found", null))
                        }
                    } catch (e: IOException) {
                        emit(Resource.error(e.message, null))
                        e.printStackTrace()
                    } catch (e: HttpException) {
                        emit(Resource.error(e.message, null))
                        e.printStackTrace()
                    } catch (e: AvailabilityDetectorException) {
                        emit(Resource.error(e.message, null))
                        e.printStackTrace()
                    } catch (e: JsonDataException) {
                        // malformed JSON response from fronyx API
                        emit(Resource.error(e.message, null))
                        e.printStackTrace()
                    }
                }
            } ?: liveData { emit(Resource.success(null)) }
        }
    }

    val predictionGraph: LiveData<Map<ZonedDateTime, Double>?> =
        MediatorLiveData<Map<ZonedDateTime, Double>?>().apply {
            listOf(prediction, availability).forEach {
                addSource(it) {
                    val congestionHistogram = availability.value?.data?.congestionHistogram
                    val prediction = prediction.value?.data
                    value = if (congestionHistogram != null && prediction == null) {
                        congestionHistogram.mapIndexed { i, value ->
                            LocalTime.of(i, 0).atDate(LocalDate.now())
                                .atZone(ZoneId.systemDefault()) to value
                        }.toMap()
                    } else {
                        prediction?.let { responses ->
                            if (responses.isEmpty()) {
                                null
                            } else {
                                val evseIds = responses.map { it.evseId }
                                val groupByTimestamp = responses.flatMap { response ->
                                    response.predictions.map {
                                        Triple(
                                            it.timestamp,
                                            response.evseId,
                                            it.status
                                        )
                                    }
                                }
                                    .groupBy { it.first }  // group by timestamp
                                    .mapValues { it.value.map { it.second to it.third } }  // only keep EVSEID and status
                                    .filterValues { it.map { it.first } == evseIds }  // remove values where status is not given for all EVSEs
                                    .filterKeys { it > ZonedDateTime.now() }  // only show predictions in the future

                                groupByTimestamp.mapValues {
                                    it.value.count {
                                        it.second == FronyxStatus.UNAVAILABLE
                                    }.toDouble()
                                }.ifEmpty { null }
                            }
                        }
                    }
            }
        }
    }

    private val predictedChargepoints = charger.map {
        it.data?.let { charger ->
            charger.chargepoints.filter {
                FronyxApi.isChargepointSupported(charger, it) &&
                        filteredConnectors.value?.let { filtered ->
                            equivalentPlugTypes(it.type).any {
                                filtered.contains(
                                    it
                                )
                            }
                        } ?: true
            }
        }
    }

    val predictionMaxValue: LiveData<Double> = MediatorLiveData<Double>().apply {
        listOf(prediction, availability).forEach {
            addSource(it) {
                value =
                    if (availability.value?.data?.congestionHistogram != null && prediction.value?.data == null) {
                        1.0
                    } else {
                        (predictedChargepoints.value?.sumOf { it.count } ?: 0).toDouble()
                    }
            }
        }
    }

    val predictionIsPercentage: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        listOf(prediction, availability).forEach {
            addSource(it) {
                value =
                    availability.value?.data?.congestionHistogram != null && prediction.value?.data == null
            }
        }
    }

    val predictionDescription: LiveData<String?> by lazy {
        predictedChargepoints.map { predictedChargepoints ->
            if (predictedChargepoints == null) return@map null
            val allChargepoints = charger.value?.data?.chargepoints ?: return@map null

            val predictedChargepointTypes = predictedChargepoints.map { it.type }.distinct()
            if (allChargepoints == predictedChargepoints) {
                null
            } else if (predictedChargepointTypes.size == 1) {
                application.getString(
                    R.string.prediction_only,
                    nameForPlugType(application.stringProvider(), predictedChargepointTypes[0])
                )
            } else {
                application.getString(
                    R.string.prediction_only,
                    application.getString(R.string.prediction_dc_plugs_only)
                )
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

    private var hasTeslaLogin: MutableLiveData<Boolean> = state.getLiveData("hasTeslaLogin")

    fun reloadPrefs() {
        filterStatus.value = prefs.filterStatus
        if (prefs.dataSource != apiId.value) {
            repo.api.value = createApi(prefs.dataSource, getApplication())
        }
        if (hasTeslaLogin.value != (encryptedPrefs.teslaAccessToken != null)) {
            hasTeslaLogin.value = encryptedPrefs.teslaAccessToken != null
            reloadAvailability()
        }
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
        chargepointLoader(pos to filters)
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

    private var chargepointsInternal: LiveData<Resource<List<ChargepointListItem>>>? = null
    private var chargepointLoader =
        throttleLatest(
            500L,
            viewModelScope
        ) { data: Pair<MapPosition, FilterValues> ->
            chargepoints.value = Resource.loading(chargepoints.value?.data)

            val mapPosition = data.first
            val filters = data.second

            val bounds = extendBounds(mapPosition.bounds)
            if (filterStatus.value == FILTERS_FAVORITES) {
                // load favorites from local DB
                val chargers = db.favoritesDao().getFavoritesInBoundsAsync(
                    bounds.southwest.latitude,
                    bounds.northeast.latitude,
                    bounds.southwest.longitude,
                    bounds.northeast.longitude
                ).map { it.charger }

                val clusterDistance = getClusterDistance(mapPosition.zoom)
                val chargersClustered = clusterDistance?.let {
                    cluster(chargers, mapPosition.zoom, clusterDistance)
                } ?: chargers
                filteredConnectors.value = null
                filteredMinPower.value = null
                filteredChargeCards.value = null
                chargepoints.value = Resource.success(chargersClustered)
                return@throttleLatest
            }

            val result = repo.getChargepoints(bounds, mapPosition.zoom, filters)
            chargepointsInternal?.let { chargepoints.removeSource(it) }
            chargepointsInternal = result
            chargepoints.addSource(result) {
                val apiId = apiId.value
                when (apiId) {
                    "goingelectric" -> {
                        val chargeCardsVal =
                            filters.getMultipleChoiceValue("chargecards") ?: return@addSource
                        filteredChargeCards.value =
                            if (chargeCardsVal.all) null else chargeCardsVal.values.map { it.toLong() }
                                .toSet()

                        val connectorsVal = filters.getMultipleChoiceValue("connectors")!!
                        filteredConnectors.value =
                            if (connectorsVal.all) null else connectorsVal.values.map {
                                GEChargepoint.convertTypeFromGE(it)
                            }.toSet()
                        filteredMinPower.value = filters.getSliderValue("min_power")
                    }

                    "openchargemap" -> {
                        val connectorsVal = filters.getMultipleChoiceValue("connectors")!!
                        filteredConnectors.value =
                            if (connectorsVal.all) null else connectorsVal.values.map {
                                OCMConnection.convertConnectionTypeFromOCM(
                                    it.toLong(),
                                    repo.referenceData.value!! as OCMReferenceData
                                )
                            }.toSet()
                        filteredMinPower.value = filters.getSliderValue("min_power")
                    }
                    else -> {
                        filteredConnectors.value = null
                        filteredMinPower.value = null
                        filteredChargeCards.value = null
                    }
                }

                chargepoints.value = it
            }
        }

    /**
     * expands LatLngBounds beyond the viewport (1.5x the width and height)
     */
    private fun extendBounds(bounds: LatLngBounds): LatLngBounds {
        val mapProjection = mapProjection ?: return bounds
        val swPoint = mapProjection.toScreenLocation(bounds.southwest)
        val nePoint = mapProjection.toScreenLocation(bounds.northeast)
        val dx = ((nePoint.x - swPoint.x) * 0.25).roundToInt()
        val dy = ((nePoint.y - swPoint.y) * 0.25).roundToInt()
        val newSw = mapProjection.fromScreenLocation(Point(swPoint.x - dx, swPoint.y - dy))
        val newNe = mapProjection.fromScreenLocation(Point(nePoint.x + dx, nePoint.y + dy))
        return LatLngBounds(newSw, newNe)
    }

    fun reloadAvailability() {
        triggerAvailabilityRefresh.value = true
    }

    fun loadChargerById(chargerId: Long) {
        chargerSparse.value = null
        repo.getChargepointDetail(chargerId).observeForever { response ->
            if (response.status == Status.SUCCESS) {
                chargerSparse.value = response.data
            }
        }
    }
}