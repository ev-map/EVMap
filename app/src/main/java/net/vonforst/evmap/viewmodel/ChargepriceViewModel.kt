package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import jsonapi.Meta
import jsonapi.Relationship
import jsonapi.Relationships
import jsonapi.ResourceIdentifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.chargeprice.*
import net.vonforst.evmap.api.equivalentPlugTypes
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.storage.PreferenceDataSource
import retrofit2.HttpException
import java.io.IOException

class ChargepriceViewModel(
    application: Application,
    chargepriceApiKey: String,
    chargepriceApiUrl: String,
    private val state: SavedStateHandle
) :
    AndroidViewModel(application) {
    private var api = ChargepriceApi.create(chargepriceApiKey, chargepriceApiUrl)
    private var prefs = PreferenceDataSource(application)

    val charger: MutableLiveData<ChargeLocation> by lazy {
        state.getLiveData("charger")
    }

    val chargepoint: MutableLiveData<Chargepoint> by lazy {
        state.getLiveData("chargepoint")
    }

    private val vehicleIds: MutableLiveData<Set<String>> by lazy {
        MutableLiveData<Set<String>>().apply {
            value = prefs.chargepriceMyVehicles
        }
    }

    val vehicles: LiveData<Resource<List<ChargepriceCar>>> by lazy {
        MediatorLiveData<Resource<List<ChargepriceCar>>>().apply {
            addSource(vehicleIds.distinctUntilChanged()) { vehicleIds ->
                if (vehicleIds.isEmpty()) {
                    value = Resource.success(emptyList())
                } else {
                    value = Resource.loading(null)
                    viewModelScope.launch {
                        value = try {
                            val result = api.getVehicles()
                            Resource.success(result.filter {
                                it.id in vehicleIds
                            })
                        } catch (e: IOException) {
                            Resource.error(e.message, null)
                        }
                    }
                }
            }
            observeForever {
                vehicle.value = it.data?.firstOrNull()
            }
        }
    }

    val vehicle: MutableLiveData<ChargepriceCar> by lazy {
        state.getLiveData("vehicle")
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

    val chargePrices: MutableLiveData<Resource<List<ChargePrice>>> by lazy {
        MediatorLiveData<Resource<List<ChargePrice>>>().apply {
            value = state["chargePrices"] ?: Resource.loading(null)
            listOf(
                batteryRange,
                batteryRangeSliderDragging,
                vehicleCompatibleConnectors,
                myTariffs, myTariffsAll, charger
            ).forEach {
                addSource(it.distinctUntilChanged()) {
                    if (!batteryRangeSliderDragging.value!!) {
                        loadPrices()
                        state["chargePrices"] = this.value
                    }
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
                        value = Resource.success(cps.data!!.mapNotNull { cp ->
                            val filteredPrices =
                                cp.chargepointPrices.filter {
                                    it.plug == getChargepricePlugType(chargepoint) && it.power == chargepoint.power
                                }
                            if (filteredPrices.isEmpty()) {
                                null
                            } else {
                                cp.copy(
                                    chargepointPrices = filteredPrices
                                )
                            }
                        }
                            .sortedBy { it.chargepointPrices.first().price ?: Double.MAX_VALUE }
                            .sortedByDescending {
                                prefs.chargepriceMyTariffsAll ||
                                        myTariffs != null && it.tariffId in myTariffs
                            }
                        )
                    }
                }
            }
        }
    }

    fun reloadPrefs() {
        vehicleIds.value = prefs.chargepriceMyVehicles
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
        val myTariffs = myTariffs.value
        val myTariffsAll = myTariffsAll.value
        if (charger == null || car == null || compatibleConnectors == null || myTariffsAll == null || myTariffsAll == false && myTariffs == null) {
            chargePrices.value = Resource.error(null, null)
            return
        }

        val cpStation = ChargepriceStation.fromEvmap(charger, compatibleConnectors)
        if (cpStation.chargePoints.isEmpty()) {
            // no compatible connectors
            chargePrices.value = Resource.success(emptyList())
            chargePriceMeta.value = Resource.success(ChargepriceMeta(emptyList()))
            return
        }

        loadPricesJob?.cancel()
        loadPricesJob = viewModelScope.launch {
            try {
                val result = api.getChargePrices(
                    ChargepriceRequest(
                        dataAdapter = ChargepriceApi.getDataAdapter(charger),
                        station = cpStation,
                        vehicle = car,
                        options = ChargepriceOptions(
                            batteryRange = batteryRange.value!!.map { it.toDouble() },
                            providerCustomerTariffs = prefs.chargepriceShowProviderCustomerTariffs,
                            maxMonthlyFees = if (prefs.chargepriceNoBaseFee) 0.0 else null,
                            currency = prefs.chargepriceCurrency,
                            allowUnbalancedLoad = prefs.chargepriceAllowUnbalancedLoad,
                            showPriceUnavailable = true
                        ),
                        relationships = if (!myTariffsAll) {
                            Relationships(
                                "tariffs" to Relationship.ToMany(
                                    (myTariffs ?: emptySet()).map {
                                        ResourceIdentifier(
                                            "tariff",
                                            id = it
                                        )
                                    },
                                    meta = Meta.from(
                                        ChargepriceRequestTariffMeta(ChargepriceInclude.ALWAYS),
                                        ChargepriceApi.moshi
                                    )
                                )
                            )
                        } else null
                    ), ChargepriceApi.getChargepriceLanguage()
                )

                val meta = result.meta!!.map(ChargepriceMeta::class.java, ChargepriceApi.moshi)!!
                chargePrices.value = Resource.success(result.data)
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
}