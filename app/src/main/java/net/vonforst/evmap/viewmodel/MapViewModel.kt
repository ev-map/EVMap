package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.availability.getAvailability
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.ChargepointList
import net.vonforst.evmap.api.goingelectric.ChargepointListItem
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.storage.AppDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.reflect.full.cast

data class MapPosition(val bounds: LatLngBounds, val zoom: Float)

class MapViewModel(application: Application, geApiKey: String) : AndroidViewModel(application) {
    private var api = GoingElectricApi.create(geApiKey)
    private var db = AppDatabase.getInstance(application)

    val bottomSheetState: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    val mapPosition: MutableLiveData<MapPosition> by lazy {
        MutableLiveData<MapPosition>()
    }
    private val filterValues: LiveData<List<FilterValue>> by lazy {
        db.filterValueDao().getFilterValues()
    }
    private val filters = getFilters(application)

    private val filtersWithValue: LiveData<List<FilterWithValue<out FilterValue>>> by lazy {
        MediatorLiveData<List<FilterWithValue<out FilterValue>>>().apply {
            addSource(filterValues) {
                val values = filterValues.value
                if (values != null) {
                    value = filters.map { filter ->
                        val value =
                            values.find { it.key == filter.key } ?: filter.defaultValue()
                        FilterWithValue(filter, filter.valueClass.cast(value))
                    }
                } else {
                    value = null
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
                        val pos = mapPosition.value ?: return@addSource
                        val filters = filtersWithValue.value ?: return@addSource
                        loadChargepoints(pos, filters)
                    }
                }
            }
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

    val favorites: LiveData<List<ChargeLocation>> by lazy {
        db.chargeLocationsDao().getAllChargeLocations()
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

    private fun loadChargepoints(
        mapPosition: MapPosition,
        filters: List<FilterWithValue<out FilterValue>>
    ) {
        chargepoints.value = Resource.loading(chargepoints.value?.data)
        val bounds = mapPosition.bounds
        val zoom = mapPosition.zoom
        getChargepointsWithFilters(bounds, zoom, filters).enqueue(object :
            Callback<ChargepointList> {
            override fun onFailure(call: Call<ChargepointList>, t: Throwable) {
                chargepoints.value = Resource.error(t.message, chargepoints.value?.data)
                t.printStackTrace()
            }

            override fun onResponse(
                call: Call<ChargepointList>,
                response: Response<ChargepointList>
            ) {
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    chargepoints.value =
                        Resource.error(response.message(), chargepoints.value?.data)
                } else {
                    chargepoints.value = Resource.success(response.body()!!.chargelocations)
                }
            }
        })
    }

    private fun getChargepointsWithFilters(
        bounds: LatLngBounds,
        zoom: Float,
        filters: List<FilterWithValue<out FilterValue>>
    ): Call<ChargepointList> {
        val freecharging =
            (filters.find { it.value.key == "freecharging" }!!.value as BooleanFilterValue).value
        val freeparking =
            (filters.find { it.value.key == "freeparking" }!!.value as BooleanFilterValue).value
        val minPower =
            (filters.find { it.value.key == "min_power" }!!.value as SliderFilterValue).value

        return api.getChargepoints(
            bounds.southwest.latitude, bounds.southwest.longitude,
            bounds.northeast.latitude, bounds.northeast.longitude,
            clustering = zoom < 13, zoom = zoom,
            clusterDistance = 70, freecharging = freecharging, minPower = minPower,
            freeparking = freeparking
        )
    }

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