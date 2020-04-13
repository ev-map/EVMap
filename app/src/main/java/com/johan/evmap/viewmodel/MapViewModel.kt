package com.johan.evmap.viewmodel

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLngBounds
import com.johan.evmap.api.availability.AvailabilityDetectorException
import com.johan.evmap.api.availability.ChargeLocationStatus
import com.johan.evmap.api.availability.availabilityDetectors
import com.johan.evmap.api.goingelectric.ChargeLocation
import com.johan.evmap.api.goingelectric.ChargepointList
import com.johan.evmap.api.goingelectric.ChargepointListItem
import com.johan.evmap.api.goingelectric.GoingElectricApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

data class MapPosition(val bounds: LatLngBounds, val zoom: Float)

class MapViewModel(geApiKey: String) : ViewModel() {
    private var api: GoingElectricApi =
        GoingElectricApi.create(geApiKey)

    val mapPosition: MutableLiveData<MapPosition> by lazy {
        MutableLiveData<MapPosition>()
    }
    val chargepoints: MediatorLiveData<Resource<List<ChargepointListItem>>> by lazy {
        MediatorLiveData<Resource<List<ChargepointListItem>>>()
            .apply {
                value = Resource.loading(emptyList())
                addSource(mapPosition) {
                    mapPosition.value?.let { pos -> loadChargepoints(pos) }
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

    private fun loadChargepoints(mapPosition: MapPosition) {
        chargepoints.value = Resource.loading(chargepoints.value?.data)
        val bounds = mapPosition.bounds
        val zoom = mapPosition.zoom
        api.getChargepoints(
            bounds.southwest.latitude, bounds.southwest.longitude,
            bounds.northeast.latitude, bounds.northeast.longitude,
            clustering = zoom < 13, zoom = zoom,
            clusterDistance = 70
        ).enqueue(object : Callback<ChargepointList> {
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

    private suspend fun loadAvailability(charger: ChargeLocation) {
        availability.value = Resource.loading(null)
        var value: Resource<ChargeLocationStatus>? = null
        withContext(Dispatchers.IO) {
            for (ad in availabilityDetectors) {
                try {
                    value = Resource.success(ad.getAvailability(charger))
                    break
                } catch (e: IOException) {
                    value = Resource.error(e.message, null)
                    e.printStackTrace()
                } catch (e: HttpException) {
                    value = Resource.error(e.message, null)
                    e.printStackTrace()
                } catch (e: AvailabilityDetectorException) {
                    value = Resource.error(e.message, null)
                    e.printStackTrace()
                }
            }
        }
        availability.value = value
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