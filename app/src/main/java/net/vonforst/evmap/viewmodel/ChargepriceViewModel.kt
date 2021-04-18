package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.banana.jsonapi2.HasOne
import net.vonforst.evmap.api.chargeprice.*
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.storage.PreferenceDataSource
import java.io.IOException
import java.util.*

class ChargepriceViewModel(application: Application, chargepriceApiKey: String) :
    AndroidViewModel(application) {
    private var api = ChargepriceApi.create(chargepriceApiKey)
    private var prefs = PreferenceDataSource(application)

    val charger: MutableLiveData<ChargeLocation> by lazy {
        MutableLiveData<ChargeLocation>()
    }

    val chargepoint: MutableLiveData<Chargepoint> by lazy {
        MutableLiveData<Chargepoint>()
    }

    val vehicle: LiveData<ChargepriceCar> by lazy {
        MutableLiveData<ChargepriceCar>().apply {
            value = prefs.chargepriceMyVehicle?.let { ChargepriceCar().apply { id = it } }
        }
    }

    val batteryRange: MutableLiveData<List<Float>> by lazy {
        MutableLiveData<List<Float>>().apply {
            value = listOf(20f, 80f)
        }
    }

    val chargePrices: MediatorLiveData<Resource<List<ChargePrice>>> by lazy {
        MediatorLiveData<Resource<List<ChargePrice>>>().apply {
            value = Resource.loading(null)
            listOf(charger, vehicle, batteryRange).forEach {
                addSource(it) {
                    loadPrices()
                }
            }
        }
    }

    val chargePriceMeta: MutableLiveData<Resource<ChargepriceMeta>> by lazy {
        MutableLiveData<Resource<ChargepriceMeta>>().apply {
            value = Resource.loading(null)
        }
    }

    val chargePricesForChargepoint: MediatorLiveData<Resource<List<ChargePrice>>> by lazy {
        MediatorLiveData<Resource<List<ChargePrice>>>().apply {
            listOf(chargePrices, chargepoint).forEach {
                addSource(it) {
                    val cps = chargePrices.value
                    val chargepoint = chargepoint.value
                    if (cps == null || chargepoint == null) {
                        value = null
                    } else if (cps.status == Status.ERROR) {
                        value = Resource.error(cps.message, null)
                    } else if (cps.status == Status.LOADING) {
                        value = Resource.loading(null)
                    } else {
                        value = Resource.success(cps.data!!.map { cp ->
                            val filteredPrices =
                                cp.chargepointPrices.filter { it.plug == chargepoint.type && it.power == chargepoint.power }
                            if (filteredPrices.isEmpty()) {
                                null
                            } else {
                                cp.clone().apply {
                                    chargepointPrices = filteredPrices
                                }
                            }
                        }.filterNotNull().sortedBy { it.chargepointPrices.first().price })
                    }
                }
            }
        }
    }

    val chargepriceMetaForChargepoint: MediatorLiveData<Resource<ChargepriceChargepointMeta>> by lazy {
        MediatorLiveData<Resource<ChargepriceChargepointMeta>>().apply {
            listOf(chargePriceMeta, chargepoint).forEach {
                addSource(it) {
                    val cpMeta = chargePriceMeta.value
                    val chargepoint = chargepoint.value
                    if (cpMeta == null || chargepoint == null) {
                        value = null
                    } else if (cpMeta.status == Status.ERROR) {
                        value = Resource.error(cpMeta.message, null)
                    } else if (cpMeta.status == Status.LOADING) {
                        value = Resource.loading(null)
                    } else {
                        value =
                            Resource.success(cpMeta.data!!.chargePoints.filter { it.plug == chargepoint.type && it.power == chargepoint.power }[0])
                    }
                }
            }
        }
    }

    private var loadPricesJob: Job? = null
    fun loadPrices() {
        chargePrices.value = Resource.loading(null)
        val geCharger = charger.value
        val car = vehicle.value
        if (geCharger == null || car == null) {
            chargePrices.value = Resource.error(null, null)
            return
        }

        loadPricesJob?.cancel()
        loadPricesJob = viewModelScope.launch {
            delay(800)
            try {
                val result = api.getChargePrices(ChargepriceRequest().apply {
                    dataAdapter = "going_electric"
                    station = ChargepriceStation.fromGoingelectric(geCharger)
                    vehicle = HasOne(car)
                    options = ChargepriceOptions(
                        batteryRange = batteryRange.value!!.map { it.toDouble() },
                        providerCustomerTariffs = prefs.chargepriceShowProviderCustomerTariffs,
                        maxMonthlyFees = if (prefs.chargepriceNoBaseFee) 0.0 else null
                    )
                }, getChargepriceLanguage())
                val meta =
                    result.meta.get<ChargepriceMeta>(ChargepriceApi.moshi.adapter(ChargepriceMeta::class.java)) as ChargepriceMeta
                chargePrices.value = Resource.success(result)
                chargePriceMeta.value = Resource.success(meta)
            } catch (e: IOException) {
                chargePrices.value = Resource.error(e.message, null)
                chargePriceMeta.value = Resource.error(e.message, null)
            }
        }
    }

    private fun getChargepriceLanguage(): String {
        val locale = Locale.getDefault().language
        return if (ChargepriceApi.supportedLanguages.contains(locale)) {
            locale
        } else {
            "en"
        }
    }
}