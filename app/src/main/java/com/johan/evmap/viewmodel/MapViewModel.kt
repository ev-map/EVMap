package com.johan.evmap.viewmodel

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLngBounds
import com.johan.evmap.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

data class MapPosition(val bounds: LatLngBounds, val zoom: Float)

class MapViewModel(geApiKey: String) : ViewModel() {
    private var api: GoingElectricApi =
        GoingElectricApi.create(geApiKey)

    val mapPosition: MutableLiveData<MapPosition> by lazy {
        MutableLiveData<MapPosition>()
    }
    val chargepoints: MediatorLiveData<List<ChargepointListItem>> by lazy {
        MediatorLiveData<List<ChargepointListItem>>()
            .apply {
                value = emptyList()
                addSource(mapPosition) {
                    mapPosition.value?.let { pos -> loadChargepoints(pos) }
                }
            }
    }

    val chargerSparse: MutableLiveData<ChargeLocation> by lazy {
        MutableLiveData<ChargeLocation>()
    }
    val chargerDetails: MediatorLiveData<ChargeLocation> by lazy {
        MediatorLiveData<ChargeLocation>().apply {
            addSource(chargerSparse) { charger ->
                if (charger != null) {
                    value = null
                    loadChargerDetails(charger)
                } else {
                    value = null
                }
            }
        }
    }
    val charger: MediatorLiveData<ChargeLocation> by lazy {
        MediatorLiveData<ChargeLocation>().apply {
            addSource(chargerSparse) { value = it }
            addSource(chargerDetails) { if (it != null) value = it }
        }
    }
    val availability: MediatorLiveData<ChargeLocationStatus> by lazy {
        MediatorLiveData<ChargeLocationStatus>().apply {
            addSource(chargerDetails) { charger ->
                if (charger != null) {
                    value = null
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
        val bounds = mapPosition.bounds
        val zoom = mapPosition.zoom
        api.getChargepoints(
            bounds.southwest.latitude, bounds.southwest.longitude,
            bounds.northeast.latitude, bounds.northeast.longitude,
            clustering = zoom < 12, zoom = zoom,
            clusterDistance = 70
        ).enqueue(object : Callback<ChargepointList> {
            override fun onFailure(call: Call<ChargepointList>, t: Throwable) {
                //TODO: show error message
                t.printStackTrace()
            }

            override fun onResponse(
                call: Call<ChargepointList>,
                response: Response<ChargepointList>
            ) {
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    //TODO: show error message
                    return
                }

                chargepoints.value = response.body()!!.chargelocations
            }
        })
    }

    private suspend fun loadAvailability(charger: ChargeLocation) {
        var value: ChargeLocationStatus? = null
        withContext(Dispatchers.IO) {
            for (ad in availabilityDetectors) {
                try {
                    value = ad.getAvailability(charger)
                    break
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: AvailabilityDetectorException) {
                    e.printStackTrace()
                }
            }
        }
        availability.value = value
    }

    private fun loadChargerDetails(charger: ChargeLocation) {
        api.getChargepointDetail(charger.id).enqueue(object :
            Callback<ChargepointList> {
            override fun onFailure(call: Call<ChargepointList>, t: Throwable) {
                //TODO: show error message
                t.printStackTrace()
            }

            override fun onResponse(
                call: Call<ChargepointList>,
                response: Response<ChargepointList>
            ) {
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    //TODO: show error message
                    return
                }

                chargerDetails.value = response.body()!!.chargelocations[0] as ChargeLocation
            }
        })
    }
}