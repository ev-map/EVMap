package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.chargeprice.ChargepriceApi
import net.vonforst.evmap.api.chargeprice.ChargepriceCar
import java.io.IOException

class SettingsViewModel(application: Application, chargepriceApiKey: String) :
    AndroidViewModel(application) {
    private var api = ChargepriceApi.create(chargepriceApiKey)

    val vehicles: MutableLiveData<Resource<List<ChargepriceCar>>> by lazy {
        MutableLiveData<Resource<List<ChargepriceCar>>>().apply {
            value = Resource.loading(null)
            loadVehicles()
        }
    }

    private fun loadVehicles() {
        viewModelScope.launch {
            try {
                val result = api.getVehicles()
                vehicles.value = Resource.success(result)
            } catch (e: IOException) {
                vehicles.value = Resource.error(e.message, null)
            }
        }
    }
}