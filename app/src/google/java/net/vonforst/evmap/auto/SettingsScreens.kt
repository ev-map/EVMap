package net.vonforst.evmap.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import net.vonforst.evmap.R
import net.vonforst.evmap.api.chargeprice.ChargepriceApi
import net.vonforst.evmap.api.chargeprice.ChargepriceCar
import net.vonforst.evmap.api.chargeprice.ChargepriceTariff
import net.vonforst.evmap.storage.PreferenceDataSource
import kotlin.math.max
import kotlin.math.min

class SettingsScreen(ctx: CarContext) : Screen(ctx) {
    val prefs = PreferenceDataSource(carContext)
    val dataSourceNames = carContext.resources.getStringArray(R.array.pref_data_source_names)
    val dataSourceValues = carContext.resources.getStringArray(R.array.pref_data_source_values)

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.auto_settings))
            setHeaderAction(Action.BACK)
            setSingleList(ItemList.Builder().apply {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_data_source))
                    val dataSourceId = prefs.dataSource
                    val dataSourceDesc = dataSourceNames[dataSourceValues.indexOf(dataSourceId)]
                    addText(dataSourceDesc)
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_settings_data_source
                            )
                        ).setTint(
                            CarColor.DEFAULT
                        ).build()
                    )
                    setBrowsable(true)
                    setOnClickListener {
                        screenManager.push(ChooseDataSourceScreen(carContext))
                    }
                }.build())
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.settings_chargeprice))
                    setImage(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                R.drawable.ic_chargeprice
                            )
                        ).setTint(
                            CarColor.DEFAULT
                        ).build()
                    )
                    setBrowsable(true)
                    setOnClickListener {
                        screenManager.push(ChargepriceSettingsScreen(carContext))
                    }
                }.build())
            }.build())
        }.build()
    }
}

class ChooseDataSourceScreen(ctx: CarContext) : Screen(ctx) {
    val prefs = PreferenceDataSource(carContext)
    val dataSourceNames = carContext.resources.getStringArray(R.array.pref_data_source_names)
    val dataSourceValues = carContext.resources.getStringArray(R.array.pref_data_source_values)
    val dataSourceDescriptions = listOf(
        carContext.getString(R.string.data_source_goingelectric_desc),
        carContext.getString(R.string.data_source_openchargemap_desc)
    )

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.pref_data_source))
            setHeaderAction(Action.BACK)
            setSingleList(ItemList.Builder().apply {
                for (i in dataSourceNames.indices) {
                    addItem(Row.Builder().apply {
                        setTitle(dataSourceNames[i])
                        addText(dataSourceDescriptions[i])
                    }.build())
                }
                setOnSelectedListener {
                    prefs.dataSource = dataSourceValues[it]
                    screenManager.pop()
                }
                setSelectedIndex(dataSourceValues.indexOf(prefs.dataSource))
            }.build())
        }.build()
    }
}

class ChargepriceSettingsScreen(ctx: CarContext) : Screen(ctx) {
    val prefs = PreferenceDataSource(carContext)
    private val maxRows = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } else 6

    override fun onGetTemplate(): Template {
        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.settings_chargeprice))
            setHeaderAction(Action.BACK)
            setSingleList(ItemList.Builder().apply {
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_my_vehicle))
                    setBrowsable(true)
                    setOnClickListener {
                        screenManager.push(SelectVehiclesScreen(carContext))
                    }
                }.build())
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_my_tariffs))
                    setBrowsable(true)
                    setOnClickListener {
                        screenManager.push(SelectTariffsScreen(carContext))
                    }
                }.build())
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.settings_android_auto_chargeprice_range))
                    setBrowsable(true)

                    val range = prefs.chargepriceBatteryRangeAndroidAuto
                    addText(
                        carContext.getString(
                            R.string.chargeprice_battery_range,
                            range[0],
                            range[1]
                        )
                    )

                    setOnClickListener {
                        screenManager.push(SelectChargingRangeScreen(carContext))
                    }
                }.build())
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_chargeprice_currency))

                    val names =
                        carContext.resources.getStringArray(R.array.pref_chargeprice_currency_names)
                    val values =
                        carContext.resources.getStringArray(R.array.pref_chargeprice_currency_values)
                    val index = values.indexOf(prefs.chargepriceCurrency)
                    addText(if (index >= 0) names[index] else "")

                    setBrowsable(true)
                    setOnClickListener {
                        screenManager.push(SelectCurrencyScreen(carContext))
                    }
                }.build())
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_chargeprice_no_base_fee))
                    setToggle(Toggle.Builder {
                        prefs.chargepriceNoBaseFee = it
                    }.setChecked(prefs.chargepriceNoBaseFee).build())
                }.build())
                addItem(Row.Builder().apply {
                    setTitle(carContext.getString(R.string.pref_chargeprice_show_provider_customer_tariffs))
                    addText(carContext.getString(R.string.pref_chargeprice_show_provider_customer_tariffs_summary))
                    setToggle(Toggle.Builder {
                        prefs.chargepriceShowProviderCustomerTariffs = it
                    }.setChecked(prefs.chargepriceShowProviderCustomerTariffs).build())
                }.build())
                if (maxRows > 6) {
                    addItem(Row.Builder().apply {
                        setTitle(carContext.getString(R.string.pref_chargeprice_allow_unbalanced_load))
                        addText(carContext.getString(R.string.pref_chargeprice_allow_unbalanced_load_summary))
                        setToggle(Toggle.Builder {
                            prefs.chargepriceAllowUnbalancedLoad = it
                        }.setChecked(prefs.chargepriceAllowUnbalancedLoad).build())
                    }.build())
                }
            }.build())
        }.build()
    }
}

class SelectVehiclesScreen(ctx: CarContext) : MultiSelectSearchScreen<ChargepriceCar>(ctx) {
    private val prefs = PreferenceDataSource(carContext)
    private var api = ChargepriceApi.create(carContext.getString(R.string.chargeprice_key))
    override val isMultiSelect = true

    override fun isSelected(it: ChargepriceCar): Boolean {
        return prefs.chargepriceMyVehicles.contains(it.id)
    }

    override fun toggleSelected(item: ChargepriceCar) {
        if (isSelected(item)) {
            prefs.chargepriceMyVehicles = prefs.chargepriceMyVehicles.minus(item.id)
        } else {
            prefs.chargepriceMyVehicles = prefs.chargepriceMyVehicles.plus(item.id)
        }
    }

    override fun getLabel(it: ChargepriceCar) = "${it.brand} ${it.name}"

    override suspend fun loadData(): List<ChargepriceCar> {
        return api.getVehicles()
    }
}

class SelectTariffsScreen(ctx: CarContext) : MultiSelectSearchScreen<ChargepriceTariff>(ctx) {
    private val prefs = PreferenceDataSource(carContext)
    private var api = ChargepriceApi.create(carContext.getString(R.string.chargeprice_key))
    override val isMultiSelect = true

    override fun isSelected(it: ChargepriceTariff): Boolean {
        return prefs.chargepriceMyTariffsAll or (prefs.chargepriceMyTariffs?.contains(it.id)
            ?: false)
    }

    override fun toggleSelected(item: ChargepriceTariff) {
        val tariffs = prefs.chargepriceMyTariffs ?: if (prefs.chargepriceMyTariffsAll) {
            fullList!!.map { it.id }.toSet()
        } else {
            emptySet()
        }
        if (isSelected(item)) {
            prefs.chargepriceMyTariffs = tariffs.minus(item.id)
            prefs.chargepriceMyTariffsAll = false
        } else {
            prefs.chargepriceMyTariffs = tariffs.plus(item.id)
            if (prefs.chargepriceMyTariffs == fullList!!.map { it.id }.toSet()) {
                prefs.chargepriceMyTariffsAll = true
            }
        }
    }

    override fun getLabel(it: ChargepriceTariff): String {
        return if (!it.name.lowercase().startsWith(it.provider.lowercase())) {
            "${it.provider} ${it.name}"
        } else {
            it.name
        }
    }

    override suspend fun loadData(): List<ChargepriceTariff> {
        return api.getTariffs()
    }
}

class SelectCurrencyScreen(ctx: CarContext) : MultiSelectSearchScreen<Pair<String, String>>(ctx) {
    private val prefs = PreferenceDataSource(carContext)
    override val isMultiSelect = false

    override fun isSelected(it: Pair<String, String>): Boolean =
        prefs.chargepriceCurrency == it.second

    override fun toggleSelected(item: Pair<String, String>) {
        prefs.chargepriceCurrency = item.second
    }

    override fun getLabel(it: Pair<String, String>): String = it.first

    override suspend fun loadData(): List<Pair<String, String>> {
        val names = carContext.resources.getStringArray(R.array.pref_chargeprice_currency_names)
        val values = carContext.resources.getStringArray(R.array.pref_chargeprice_currency_values)
        return names.zip(values)
    }
}

class SelectChargingRangeScreen(ctx: CarContext) : Screen(ctx) {
    val prefs = PreferenceDataSource(carContext)
    private val maxItems = if (ctx.carAppApiLevel >= 2) {
        ctx.constraintManager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
    } else 6

    override fun onGetTemplate(): Template {
        return GridTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.settings_android_auto_chargeprice_range))
            setHeaderAction(Action.BACK)
            setSingleList(
                ItemList.Builder().apply {
                    addItem(GridItem.Builder().apply {
                        setTitle(carContext.getString(R.string.chargeprice_battery_range_from))
                        setText(
                            carContext.getString(
                                R.string.percent_format,
                                prefs.chargepriceBatteryRangeAndroidAuto[0]
                            )
                        )
                        setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_add
                                )
                            ).build()
                        )
                        setOnClickListener {
                            prefs.chargepriceBatteryRangeAndroidAuto =
                                prefs.chargepriceBatteryRangeAndroidAuto.toMutableList().apply {
                                    this[0] = min(this[1] - 5, this[0] + 5)
                                }
                            invalidate()
                        }
                    }.build())
                    addItem(GridItem.Builder().apply {
                        setTitle(carContext.getString(R.string.chargeprice_battery_range_to))
                        setText(
                            carContext.getString(
                                R.string.percent_format,
                                prefs.chargepriceBatteryRangeAndroidAuto[1]
                            )
                        )
                        setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_add
                                )
                            ).build()
                        )
                        setOnClickListener {
                            prefs.chargepriceBatteryRangeAndroidAuto =
                                prefs.chargepriceBatteryRangeAndroidAuto.toMutableList().apply {
                                    this[1] = min(100f, this[1] + 5)
                                }
                            invalidate()
                        }
                    }.build())

                    val nSpacers = when {
                        maxItems % 3 == 0 -> 1
                        maxItems % 4 == 0 -> 2
                        else -> 0
                    }

                    for (i in 0..nSpacers) {
                        addItem(GridItem.Builder().apply {
                            setTitle(" ")
                            setImage(emptyCarIcon)
                        }.build())
                    }

                    addItem(GridItem.Builder().apply {
                        setTitle(" ")
                        setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_remove
                                )
                            ).build()
                        )
                        setOnClickListener {
                            prefs.chargepriceBatteryRangeAndroidAuto =
                                prefs.chargepriceBatteryRangeAndroidAuto.toMutableList().apply {
                                    this[0] = max(0f, this[0] - 5)
                                }
                            invalidate()
                        }
                    }.build())
                    addItem(GridItem.Builder().apply {
                        setTitle(" ")
                        setImage(
                            CarIcon.Builder(
                                IconCompat.createWithResource(
                                    carContext,
                                    R.drawable.ic_remove
                                )
                            ).build()
                        )
                        setOnClickListener {
                            prefs.chargepriceBatteryRangeAndroidAuto =
                                prefs.chargepriceBatteryRangeAndroidAuto.toMutableList().apply {
                                    this[1] = max(this[0] + 5, this[1] - 5)
                                }
                            invalidate()
                        }
                    }.build())
                }.build()
            )
        }.build()
    }
}