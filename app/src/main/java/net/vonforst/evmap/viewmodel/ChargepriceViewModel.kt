package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.banana.jsonapi2.HasOne
import net.vonforst.evmap.api.chargeprice.*
import net.vonforst.evmap.api.equivalentPlugTypes
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.storage.PreferenceDataSource
import retrofit2.HttpException
import java.io.IOException
import java.util.*

class ChargepriceViewModel(application: Application, chargepriceApiKey: String) :
    AndroidViewModel(application) {
    private var api = ChargepriceApi.create(chargepriceApiKey)
    private var prefs = PreferenceDataSource(application)

    val charger: MutableLiveData<ChargeLocation> by lazy {
        MutableLiveData<ChargeLocation>()
    }

    val dataSource: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    val chargepoint: MutableLiveData<Chargepoint> by lazy {
        MutableLiveData<Chargepoint>()
    }

    val vehicles: MutableLiveData<Resource<List<ChargepriceCar>>> by lazy {
        MutableLiveData<Resource<List<ChargepriceCar>>>().apply {
            if (prefs.chargepriceMyVehicles.isEmpty()) {
                value = Resource.success(emptyList())
            } else {
                value = Resource.loading(null)
                loadVehicles()
            }
            observeForever {
                vehicle.value = it.data?.firstOrNull()
            }
        }
    }

    val vehicle: MutableLiveData<ChargepriceCar> by lazy {
        MutableLiveData<ChargepriceCar>()
    }

    val vehicleCompatibleConnectors: LiveData<List<String>> by lazy {
        MediatorLiveData<List<String>>().apply {
            addSource(vehicle) {
                value = it?.compatibleEvmapConnectors
            }
        }
    }

    val noCompatibleConnectors: LiveData<Boolean> by lazy {
        MediatorLiveData<Boolean>().apply {
            value = false
            listOf(charger, vehicleCompatibleConnectors).forEach {
                addSource(it) {
                    val charger = charger.value ?: return@addSource
                    val connectors = vehicleCompatibleConnectors.value ?: return@addSource
                    value = !charger.chargepoints.flatMap { equivalentPlugTypes(it.type) }
                        .any { it in connectors }
                }
            }
        }
    }

    val batteryRange: MutableLiveData<List<Float>> by lazy {
        MutableLiveData<List<Float>>().apply {
            value = prefs.chargepriceBatteryRange
            observeForever {
                if (it[0] == it[1]) {
                    value = if (it[0] < 1.0) {
                        listOf(it[0], it[1] + 1)
                    } else {
                        listOf(it[0] - 1, it[1])
                    }
                }
                prefs.chargepriceBatteryRange = value!!
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
                dataSource,
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
                                cp.chargepointPrices.filter {
                                    it.plug == getChargepricePlugType(chargepoint) && it.power == chargepoint.power
                                }
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

    private fun getChargepricePlugType(chargepoint: Chargepoint): String {
        val index = charger.value!!.chargepointsMerged.indexOf(chargepoint)
        val type = charger.value!!.chargepriceData!!.plugTypes?.get(index) ?: chargepoint.type
        return type
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
                        val result = cpMeta.data!!.chargePoints.filter {
                            it.plug == getChargepricePlugType(
                                chargepoint
                            ) && it.power == chargepoint.power
                        }.elementAtOrNull(0)
                        value = if (result != null) {
                            Resource.success(result)
                        } else {
                            Resource.error("matching chargepoint not found", null)
                        }
                    }
                }
            }
        }
    }

    private var loadPricesJob: Job? = null
    fun loadPrices() {
        chargePrices.value = Resource.loading(null)
        chargePriceMeta.value = Resource.loading(null)
        val charger = charger.value
        val car = vehicle.value
        val compatibleConnectors = vehicleCompatibleConnectors.value
        val dataSource = dataSource.value
        if (charger == null || car == null || compatibleConnectors == null || dataSource == null) {
            chargePrices.value = Resource.error(null, null)
            return
        }

        val cpStation = ChargepriceStation.fromEvmap(charger, compatibleConnectors)

        loadPricesJob?.cancel()
        loadPricesJob = viewModelScope.launch {
            try {
                val result = api.getChargePrices(ChargepriceRequest().apply {
                    dataAdapter = dataSource
                    station = cpStation
                    vehicle = HasOne(car)
                    options = ChargepriceOptions(
                        batteryRange = batteryRange.value!!.map { it.toDouble() },
                        providerCustomerTariffs = prefs.chargepriceShowProviderCustomerTariffs,
                        maxMonthlyFees = if (prefs.chargepriceNoBaseFee) 0.0 else null,
                        currency = prefs.chargepriceCurrency
                    )
                }, ChargepriceApi.getChargepriceLanguage())
                val meta =
                    result.meta.get<ChargepriceMeta>(ChargepriceApi.moshi.adapter(ChargepriceMeta::class.java)) as ChargepriceMeta
                chargePrices.value = Resource.success(result)
                chargePriceMeta.value = Resource.success(meta)
            } catch (e: IOException) {
                chargePrices.value = Resource.error(e.message, null)
                chargePriceMeta.value = Resource.error(e.message, null)
            } catch (e: HttpException) {
                chargePrices.value = Resource.error(e.message, null)
                chargePriceMeta.value = Resource.error(e.message, null)
            }
        }
    }

    private fun loadVehicles() {
        viewModelScope.launch {
            try {
                val result = api.getVehicles()
                vehicles.value = Resource.success(result.filter {
                    it.id in prefs.chargepriceMyVehicles
                })
            } catch (e: IOException) {
                vehicles.value = Resource.error(e.message, null)
            }
        }
    }
}