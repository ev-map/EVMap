package net.vonforst.evmap.auto

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.Model
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.banana.jsonapi2.HasMany
import moe.banana.jsonapi2.HasOne
import moe.banana.jsonapi2.JsonBuffer
import moe.banana.jsonapi2.ResourceIdentifier
import net.vonforst.evmap.*
import net.vonforst.evmap.api.chargeprice.*
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.currency
import java.io.IOException

class ChargepriceScreen(ctx: CarContext, val charger: ChargeLocation) : Screen(ctx) {
    private val prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(carContext)
    private val api by lazy {
        ChargepriceApi.create(carContext.getString(R.string.chargeprice_key))
    }
    private var prices: List<ChargePrice>? = null
    private var meta: ChargepriceChargepointMeta? = null
    private val maxRows = 6
    private var errorMessage: String? = null
    private val batteryRange = listOf(20.0, 80.0)

    override fun onGetTemplate(): Template {
        if (prices == null) loadData()

        return ListTemplate.Builder().apply {
            setTitle(
                carContext.getString(
                    R.string.chargeprice_battery_range,
                    batteryRange[0],
                    batteryRange[1]
                ) + " · " + carContext.getString(R.string.powered_by_chargeprice)
            )
            setHeaderAction(Action.BACK)
            if (prices == null && errorMessage == null) {
                setLoading(true)
            } else {
                setSingleList(ItemList.Builder().apply {
                    setNoItemsMessage(
                        errorMessage ?: carContext.getString(R.string.chargeprice_no_tariffs_found)
                    )
                    prices?.take(maxRows)?.forEach { price ->
                        addItem(Row.Builder().apply {
                            setTitle(formatProvider(price))
                            addText(formatPrice(price))
                        }.build())
                    }
                }.build())
            }
            setActionStrip(
                ActionStrip.Builder().addAction(
                    Action.Builder().setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_chargeprice
                            )
                        ).build()
                    ).setOnClickListener {
                        val intent = CustomTabsIntent.Builder()
                            .setDefaultColorSchemeParams(
                                CustomTabColorSchemeParams.Builder()
                                    .setToolbarColor(
                                        ContextCompat.getColor(
                                            carContext,
                                            R.color.colorPrimary
                                        )
                                    )
                                    .build()
                            )
                            .build().intent
                        intent.data =
                            Uri.parse("https://www.chargeprice.app/?poi_id=${charger.id}&poi_source=${getDataAdapter()}")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            carContext.startActivity(intent)
                            CarToast.makeText(
                                carContext,
                                R.string.opened_on_phone,
                                CarToast.LENGTH_LONG
                            ).show()
                        } catch (e: ActivityNotFoundException) {
                            CarToast.makeText(
                                carContext,
                                R.string.no_browser_app_found,
                                CarToast.LENGTH_LONG
                            ).show()
                        }
                    }.build()
                ).build()
            )
        }.build()
    }

    private fun formatProvider(price: ChargePrice): String {
        if (!price.tariffName.startsWith(price.provider)) {
            return price.provider + " " + price.tariffName
        } else {
            return price.tariffName
        }
    }

    private fun formatPrice(price: ChargePrice): String {
        val totalPrice = carContext.getString(
            R.string.charge_price_format,
            price.chargepointPrices.first().price,
            currency(price.currency)
        )
        val kwhPrice = if (price.chargepointPrices.first().price > 0f) {
            carContext.getString(
                if (price.chargepointPrices[0].priceDistribution.isOnlyKwh) {
                    R.string.charge_price_kwh_format
                } else {
                    R.string.charge_price_average_format
                },
                price.chargepointPrices.get(0).price / meta!!.energy,
                currency(price.currency)
            )
        } else null
        val monthlyFees = if (price.totalMonthlyFee > 0 || price.monthlyMinSales > 0) {
            price.formatMonthlyFees(carContext)
        } else null
        var text = totalPrice
        if (kwhPrice != null && monthlyFees != null) {
            text += " ($kwhPrice, $monthlyFees)"
        } else if (kwhPrice != null) {
            text += " ($kwhPrice)"
        } else if (monthlyFees != null) {
            text += " ($monthlyFees)"
        }
        return text
    }

    private fun loadData() {
        if (supportsCarApiLevel3(carContext)) {
            val exec = ContextCompat.getMainExecutor(carContext)
            val hardwareMan =
                carContext.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
            hardwareMan.carInfo.fetchModel(exec) { model ->
                loadPrices(model)
            }
        } else {
            loadPrices(null)
        }
    }

    private fun loadPrices(model: Model?) {
        val dataAdapter = getDataAdapter() ?: return
        val manufacturer = model?.manufacturer?.value
        val modelName = model?.name?.value
        lifecycleScope.launch {
            try {
                var vehicles = api.getVehicles().filter {
                    it.id in prefs.chargepriceMyVehicles
                }
                if (vehicles.isEmpty()) {
                    errorMessage = carContext.getString(R.string.chargeprice_select_car_first)
                    invalidate()
                    return@launch
                } else if (vehicles.size > 1) {
                    if (manufacturer != null && modelName != null) {
                        vehicles = vehicles.filter {
                            it.brand == manufacturer && it.name.startsWith(modelName)
                        }
                        if (vehicles.isEmpty()) {
                            errorMessage = carContext.getString(
                                R.string.auto_chargeprice_vehicle_unknown,
                                manufacturer,
                                modelName
                            )
                            invalidate()
                            return@launch
                        } else if (vehicles.size > 1) {
                            errorMessage = carContext.getString(
                                R.string.auto_chargeprice_vehicle_ambiguous,
                                manufacturer,
                                modelName
                            )
                            invalidate()
                            return@launch
                        }
                    } else {
                        errorMessage =
                            carContext.getString(R.string.auto_chargeprice_vehicle_unavailable)
                        invalidate()
                        return@launch
                    }
                }
                val car = vehicles[0]

                val cpStation = ChargepriceStation.fromEvmap(charger, car.compatibleEvmapConnectors)
                val result = api.getChargePrices(ChargepriceRequest().apply {
                    this.dataAdapter = dataAdapter
                    station = cpStation
                    vehicle = HasOne(car)
                    tariffs = if (!prefs.chargepriceMyTariffsAll) {
                        val myTariffs = prefs.chargepriceMyTariffs ?: emptySet()
                        HasMany<ChargepriceTariff>(*myTariffs.map {
                            ResourceIdentifier(
                                "tariff",
                                it
                            )
                        }.toTypedArray()).apply {
                            meta = JsonBuffer.create(
                                ChargepriceApi.moshi.adapter(ChargepriceRequestTariffMeta::class.java),
                                ChargepriceRequestTariffMeta(ChargepriceInclude.ALWAYS)
                            )
                        }
                    } else null
                    options = ChargepriceOptions(
                        batteryRange = batteryRange,
                        providerCustomerTariffs = prefs.chargepriceShowProviderCustomerTariffs,
                        maxMonthlyFees = if (prefs.chargepriceNoBaseFee) 0.0 else null,
                        currency = prefs.chargepriceCurrency
                    )
                }, ChargepriceApi.getChargepriceLanguage())

                val myTariffs = prefs.chargepriceMyTariffs

                // choose the highest power chargepoint compatible with the car
                val chargepoint = cpStation.chargePoints.filterIndexed { i, cp ->
                    charger.chargepointsMerged[i].type in car.compatibleEvmapConnectors
                }.maxByOrNull { it.power }
                if (chargepoint == null) {
                    errorMessage =
                        carContext.getString(R.string.chargeprice_no_compatible_connectors)
                    invalidate()
                    return@launch
                }
                meta =
                    (result.meta.get<ChargepriceMeta>(ChargepriceApi.moshi.adapter(ChargepriceMeta::class.java)) as ChargepriceMeta).chargePoints.filterIndexed { i, cp ->
                        charger.chargepointsMerged[i].type in car.compatibleEvmapConnectors
                    }.maxByOrNull {
                        it.power
                    }

                prices = result.map { cp ->
                    val filteredPrices =
                        cp.chargepointPrices.filter {
                            it.plug == chargepoint.plug && it.power == chargepoint.power
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
                invalidate()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    CarToast.makeText(
                        carContext,
                        R.string.chargeprice_connection_error,
                        CarToast.LENGTH_LONG
                    )
                        .show()
                }
            }
        }
    }

    private fun getDataAdapter(): String? = when (charger.dataSource) {
        "goingelectric" -> ChargepriceApi.DATA_SOURCE_GOINGELECTRIC
        "openchargemap" -> ChargepriceApi.DATA_SOURCE_OPENCHARGEMAP
        else -> null
    }
}