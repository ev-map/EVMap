package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Job
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

    private val acConnectors = listOf(
        Chargepoint.CEE_BLAU,
        Chargepoint.CEE_ROT,
        Chargepoint.SCHUKO,
        Chargepoint.TYPE_1,
        Chargepoint.TYPE_2
    )
    private val plugMapping = mapOf(
        "ccs" to Chargepoint.CCS,
        "tesla_suc" to Chargepoint.SUPERCHARGER,
        "tesla_ccs" to Chargepoint.CCS,
        "chademo" to Chargepoint.CHADEMO
    )
    val vehicleCompatibleConnectors: LiveData<List<String>> by lazy {
        MutableLiveData<List<String>>().apply {
            value = prefs.chargepriceMyVehicleDcChargeports?.map {
                plugMapping.get(it)
            }?.filterNotNull()?.plus(acConnectors)
        }
    }

    val noCompatibleConnectors: LiveData<Boolean> by lazy {
        MediatorLiveData<Boolean>().apply {
            value = false
            listOf(charger, vehicleCompatibleConnectors).forEach {
                addSource(it) {
                    val charger = charger.value ?: return@addSource
                    val connectors = vehicleCompatibleConnectors.value ?: return@addSource
                    value = !charger.chargepoints.map { it.type }.any { it in connectors }
                }
            }
        }
    }

    val batteryRange: MutableLiveData<List<Float>> by lazy {
        MutableLiveData<List<Float>>().apply {
            value = prefs.chargepriceBatteryRange
            observeForever {
                prefs.chargepriceBatteryRange = it
            }
        }
    }
    val batteryRangeSliderDragging: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().apply {
            value = false
        }
    }

    val chargePrices: MediatorLiveData<Resource<List<ChargePrice>>> by lazy {
        MediatorLiveData<Resource<List<ChargePrice>>>().apply {
            value = Resource.loading(null)
            listOf(
                charger,
                vehicle,
                batteryRange,
                batteryRangeSliderDragging,
                vehicleCompatibleConnectors
            ).forEach {
                addSource(it) {
                    if (!batteryRangeSliderDragging.value!!) loadPrices()
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
                        val myTariffs = prefs.chargepriceMyTariffs
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
                        }.filterNotNull()
                            .sortedBy { it.chargepointPrices.first().price }
                            .sortedByDescending {
                                prefs.chargepriceMyTariffsAll ||
                                        myTariffs != null && it.tariff?.get()?.id in myTariffs
                            }
                        )
                    }
                }
            }
        }
    }

    val myTariffs: LiveData<Set<String>> by lazy {
        MutableLiveData<Set<String>>().apply {
            value = prefs.chargepriceMyTariffs
        }
    }
    val myTariffsAll: LiveData<Boolean> by lazy {
        MutableLiveData<Boolean>().apply {
            value = prefs.chargepriceMyTariffsAll
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
        val compatibleConnectors = vehicleCompatibleConnectors.value
        if (geCharger == null || car == null || compatibleConnectors == null) {
            chargePrices.value = Resource.error(null, null)
            return
        }

        val cpStation = ChargepriceStation.fromGoingelectric(geCharger, compatibleConnectors)

        loadPricesJob?.cancel()
        loadPricesJob = viewModelScope.launch {
            try {
                val result = api.getChargePrices(ChargepriceRequest().apply {
                    dataAdapter = "going_electric"
                    station = cpStation
                    vehicle = HasOne(car)
                    options = ChargepriceOptions(
                        batteryRange = batteryRange.value!!.map { it.toDouble() },
                        providerCustomerTariffs = prefs.chargepriceShowProviderCustomerTariffs,
                        maxMonthlyFees = if (prefs.chargepriceNoBaseFee) 0.0 else null,
                        currency = prefs.chargepriceCurrency
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