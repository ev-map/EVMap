package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import net.vonforst.evmap.R
import net.vonforst.evmap.api.chargeprice.*
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.currency
import java.io.IOException

enum class ChargepriceFeedbackType {
    MISSING_PRICE, WRONG_PRICE, MISSING_VEHICLE
}

class ChargepriceFeedbackViewModel(
    application: Application,
    chargepriceApiKey: String,
    chargepriceApiUrl: String
) :
    AndroidViewModel(application) {
    private var api = ChargepriceApi.create(chargepriceApiKey, chargepriceApiUrl)
    private var prefs = PreferenceDataSource(application)

    // data supplied through fragment args
    val feedbackType = MutableLiveData<ChargepriceFeedbackType>()
    val charger = MutableLiveData<ChargeLocation>()
    val chargepoint = MutableLiveData<Chargepoint>()
    val vehicle = MutableLiveData<ChargepriceCar>()
    val chargePrices = MutableLiveData<List<ChargePrice>>()
    val batteryRange = MutableLiveData<List<Float>>()

    // data input by user
    val tariff = MutableLiveData<String>()
    val price = MutableLiveData<String>()
    val notes = MutableLiveData<String>()
    val email = MutableLiveData<String>()

    val loading = MutableLiveData<Boolean>().apply { value = false }

    val chargePricesStrings = chargePrices.map {
        it.map {
            val name = if (!it.tariffName.lowercase().startsWith(it.provider.lowercase())) {
                "${it.provider} ${it.tariffName}"
            } else it.tariffName
            val price = application.getString(
                R.string.charge_price_format,
                it.chargepointPrices[0].price,
                currency(it.currency)
            )
            "$name: $price"
        }.toList()
    }

    private val feedback = MediatorLiveData<ChargepriceUserFeedback>().apply {
        listOf(
            feedbackType,
            charger,
            chargepoint,
            vehicle,
            chargePrices,
            tariff,
            price,
            notes,
            email
        ).forEach {
            addSource(it) {
                try {
                    value = when (feedbackType.value!!) {
                        ChargepriceFeedbackType.MISSING_PRICE -> {
                            ChargepriceMissingPriceFeedback(
                                tariff.value ?: "",
                                charger.value?.network?.take(200) ?: "",
                                price.value ?: "",
                                charger.value?.let { ChargepriceApi.getPoiUrl(it) } ?: "",
                                notes.value ?: "",
                                email.value ?: "",
                                getChargepriceContext(),
                                ChargepriceApi.getChargepriceLanguage()
                            )
                        }
                        ChargepriceFeedbackType.WRONG_PRICE -> {
                            ChargepriceWrongPriceFeedback(
                                "",  // TODO: dropdown value
                                charger.value?.network?.take(200) ?: "",
                                "",  // TODO: dropdown value
                                price.value ?: "",
                                charger.value?.let { ChargepriceApi.getPoiUrl(it) } ?: "",
                                notes.value ?: "",
                                email.value ?: "",
                                getChargepriceContext(),
                                ChargepriceApi.getChargepriceLanguage()
                            )
                        }
                        ChargepriceFeedbackType.MISSING_VEHICLE -> {
                            TODO()
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    value = null
                }
            }
        }
    }

    val formValid = feedback.map { it != null }

    fun sendFeedback() {
        val feedback = feedback.value ?: return
        viewModelScope.launch {
            loading.value = true
            try {
                api.userFeedback(feedback)
            } catch (e: IOException) {

            }
            loading.value = false
        }
    }

    private fun getChargepriceContext(): String {
        val result = StringBuilder()
        vehicle.value?.let { result.append("Vehicle: ${it.brand} ${it.name}\n") }
        batteryRange.value?.let { result.append("Battery SOC: ${it[0]} to ${it[1]}\n") }
        return result.toString()
    }
}