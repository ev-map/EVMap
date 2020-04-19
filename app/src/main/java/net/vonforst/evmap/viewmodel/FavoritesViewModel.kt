package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.storage.AppDatabase

class FavoritesViewModel(application: Application, geApiKey: String) :
    AndroidViewModel(application) {
    private var api = GoingElectricApi.create(geApiKey)
    private var db = AppDatabase.getInstance(application)

    val favorites: LiveData<List<ChargeLocation>> by lazy {
        db.chargeLocationsDao().getAllChargeLocations()
    }

    val location: MutableLiveData<LatLng> by lazy {
        MutableLiveData<LatLng>()
    }

    /*val availability: MediatorLiveData<Map<Long, Resource<ChargeLocationStatus>>> by lazy {
        MediatorLiveData<Map<Long, Resource<ChargeLocationStatus>>>().apply {
            addSource(favorites) { chargers ->
                if (chargers != null) {
                    viewModelScope.launch {
                        chargers.map {
                            availability.value = Resource.loading(null)
                        }
                    }
                } else {
                    value = null
                }
            }
        }
    }*/

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
}