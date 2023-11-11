package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.chargeprice.ChargepriceApi
import net.vonforst.evmap.api.chargeprice.ChargepriceCar
import net.vonforst.evmap.api.chargeprice.ChargepriceTariff
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import retrofit2.HttpException
import java.io.IOException

class SettingsViewModel(
    application: Application,
    chargepriceApiKey: String,
    chargepriceApiUrl: String
) :
    AndroidViewModel(application) {
    private val api = ChargepriceApi.create(chargepriceApiKey, chargepriceApiUrl)
    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferenceDataSource(application)

    val vehicles: MutableLiveData<Resource<List<ChargepriceCar>>> by lazy {
        MutableLiveData<Resource<List<ChargepriceCar>>>().apply {
            value = Resource.loading(null)
            loadVehicles()
        }
    }

    val tariffs: MutableLiveData<Resource<List<ChargepriceTariff>>> by lazy {
        MutableLiveData<Resource<List<ChargepriceTariff>>>().apply {
            value = Resource.loading(null)
            loadTariffs()
        }
    }

    val chargerCacheCount: LiveData<Long> by lazy {
        db.chargeLocationsDao().getCount()
    }

    val chargerCacheSize: LiveData<Long> by lazy {
        MutableLiveData<Long>().apply {
            chargerCacheCount.observeForever {
                viewModelScope.launch {
                    value = db.chargeLocationsDao().getSize()
                }
            }
        }
    }

    private fun loadVehicles() {
        viewModelScope.launch {
            try {
                val result = api.getVehicles()
                vehicles.value = Resource.success(result)
            } catch (e: IOException) {
                vehicles.value = Resource.error(e.message, null)
            } catch (e: HttpException) {
                vehicles.value = Resource.error(e.message, null)
            }
        }
    }

    private fun loadTariffs() {
        viewModelScope.launch {
            try {
                val result = api.getTariffs()
                tariffs.value = Resource.success(result)
            } catch (e: IOException) {
                tariffs.value = Resource.error(e.message, null)
            } catch (e: HttpException) {
                tariffs.value = Resource.error(e.message, null)
            }
        }
    }

    fun deleteRecentSearchResults() {
        viewModelScope.launch {
            db.recentAutocompletePlaceDao().deleteAll()
        }
    }

    fun clearChargerCache() {
        viewModelScope.launch {
            db.savedRegionDao().deleteAll()
            db.chargeLocationsDao().deleteAllIfNotFavorite()
        }
    }
}