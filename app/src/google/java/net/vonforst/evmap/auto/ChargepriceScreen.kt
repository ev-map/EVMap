package net.vonforst.evmap.auto

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.info.Model
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import jsonapi.Meta
import jsonapi.Relationship
import jsonapi.Relationships
import jsonapi.ResourceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.vonforst.evmap.R
import net.vonforst.evmap.api.chargeprice.*
import net.vonforst.evmap.api.equivalentPlugTypes
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.ui.currency
import net.vonforst.evmap.ui.time
import java.io.IOException
import kotlin.math.roundToInt

class ChargepriceScreen(ctx: CarContext, val charger: ChargeLocation) : Screen(ctx) {
    private val prefs = PreferenceDataSource(ctx)
    private val db = AppDatabase.getInstance(carContext)
    private val api by lazy {
        ChargepriceApi.create(
            carContext.getString(R.string.chargeprice_key),
            carContext.getString(R.string.chargeprice_api_url)
        )
    }
    private var prices: List<ChargePrice>? = null
    private var meta: ChargepriceChargepointMeta? = null
    private var chargepoint: Chargepoint? = null
    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else 6
    private var errorMessage: String? = null
    private val batteryRange = prefs.chargepriceBatteryRangeAndroidAuto

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
                val header = meta?.let { meta ->
                    chargepoint?.let { chargepoint ->
                        "${
                            nameForPlugType(
                                carContext.stringProvider(),
                                chargepoint.type
                            )
                        } ${chargepoint.formatPower()} " + carContext.getString(
                            R.string.chargeprice_stats,
                            meta.energy,
                            time(meta.duration.roundToInt()),
                            meta.energy / meta.duration * 60
                        )
                    }
                } ?: ""
                addSectionedList(SectionedItemList.create(ItemList.Builder().apply {
                    setNoItemsMessage(
                        errorMessage ?: carContext.getString(R.string.chargeprice_no_tariffs_found)
                    )
                    prices?.take(maxRows)?.forEach { price ->
                        addItem(Row.Builder().apply {
                            setTitle(formatProvider(price))
                            addText(formatPrice(price))
                        }.build())
                    }
                }.build(), header))
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
                            Uri.parse(ChargepriceApi.getPoiUrl(charger))
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
        val dataAdapter = ChargepriceApi.getDataAdapter(charger) ?: return
        val manufacturer = model?.manufacturer?.value
        val modelName = getVehicleModel(model?.manufacturer?.value, model?.name?.value)
        lifecycleScope.launch {
            try {
                val car = determineVehicle(manufacturer, modelName)
                val cpStation = ChargepriceStation.fromEvmap(charger, car.compatibleEvmapConnectors)

                if (cpStation.chargePoints.isEmpty()) {
                    errorMessage =
                        carContext.getString(R.string.chargeprice_no_compatible_connectors)
                    invalidate()
                    return@launch
                }

                val result = api.getChargePrices(
                    ChargepriceRequest(
                        dataAdapter = dataAdapter,
                        station = cpStation,
                        vehicle = car,
                        options = ChargepriceOptions(
                            batteryRange = batteryRange.map { it.toDouble() },
                            providerCustomerTariffs = prefs.chargepriceShowProviderCustomerTariffs,
                            maxMonthlyFees = if (prefs.chargepriceNoBaseFee) 0.0 else null,
                            currency = prefs.chargepriceCurrency,
                            allowUnbalancedLoad = prefs.chargepriceAllowUnbalancedLoad
                        ),
                        relationships = if (!prefs.chargepriceMyTariffsAll) {
                            val myTariffs = prefs.chargepriceMyTariffs ?: emptySet()
                            Relationships(
                                "tariffs" to Relationship.ToMany(
                                    myTariffs.map {
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

                val myTariffs = prefs.chargepriceMyTariffs

                // choose the highest power chargepoint
                // (we have already filtered so that only compatible ones are included)
                val chargepoint = cpStation.chargePoints.maxByOrNull { it.power }

                val index = cpStation.chargePoints.indexOf(chargepoint)
                this@ChargepriceScreen.chargepoint =
                    charger.chargepoints.filter { equivalentPlugTypes(it.type).any { it in car.compatibleEvmapConnectors } }[index]

                if (chargepoint == null) {
                    errorMessage =
                        carContext.getString(R.string.chargeprice_no_compatible_connectors)
                    invalidate()
                    return@launch
                }

                val metaMapped =
                    result.meta!!.map(ChargepriceMeta::class.java, ChargepriceApi.moshi)!!
                meta = metaMapped.chargePoints.maxByOrNull { it.power }

                prices = result.data!!.map { cp ->
                    val filteredPrices =
                        cp.chargepointPrices.filter {
                            it.plug == chargepoint.plug && it.power == chargepoint.power
                        }
                    if (filteredPrices.isEmpty()) {
                        null
                    } else {
                        cp.copy(
                            chargepointPrices = filteredPrices
                        )
                    }
                }.filterNotNull()
                    .sortedBy { it.chargepointPrices.first().price }
                    .sortedByDescending {
                        prefs.chargepriceMyTariffsAll ||
                                myTariffs != null && it.tariffId in myTariffs
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
            } catch (e: NoVehicleSelectedException) {
                errorMessage = carContext.getString(R.string.chargeprice_select_car_first)
                invalidate()
            } catch (e: VehicleUnknownException) {
                errorMessage = carContext.getString(
                    R.string.auto_chargeprice_vehicle_unknown,
                    manufacturer,
                    modelName
                )
                invalidate()
            } catch (e: VehicleAmbiguousException) {
                errorMessage = carContext.getString(
                    R.string.auto_chargeprice_vehicle_ambiguous,
                    manufacturer,
                    modelName
                )
                invalidate()
            } catch (e: VehicleUnavailableException) {
                errorMessage =
                    carContext.getString(R.string.auto_chargeprice_vehicle_unavailable)
                invalidate()
            }
        }
    }

    private class NoVehicleSelectedException : Exception()
    private class VehicleUnknownException : Exception()
    private class VehicleAmbiguousException : Exception()
    private class VehicleUnavailableException : Exception()

    private suspend fun determineVehicle(
        manufacturer: String?,
        modelName: String?
    ): ChargepriceCar {
        var vehicles = api.getVehicles().filter {
            it.id in prefs.chargepriceMyVehicles
        }
        if (vehicles.isEmpty()) {
            throw NoVehicleSelectedException()
        } else if (vehicles.size > 1) {
            if (manufacturer != null) {
                vehicles = vehicles.filter {
                    it.brand == manufacturer
                }
                if (vehicles.isEmpty()) {
                    throw VehicleUnknownException()
                } else if (vehicles.size > 1) {
                    if (modelName != null) {
                        vehicles = vehicles.filter {
                            it.name.startsWith(modelName)
                        }
                        if (vehicles.isEmpty()) {
                            throw VehicleUnknownException()
                        } else if (vehicles.size > 1) {
                            throw VehicleAmbiguousException()
                        }
                    } else {
                        throw VehicleAmbiguousException()
                    }
                }
            } else {
                throw VehicleUnavailableException()
            }
        }
        return vehicles[0]
    }
}