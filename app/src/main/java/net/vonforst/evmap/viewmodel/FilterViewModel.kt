package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.databinding.BaseObservable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.api.goingelectric.ChargeCard
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.storage.*
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

val powerSteps = listOf(0, 2, 3, 7, 11, 22, 43, 50, 75, 100, 150, 200, 250, 300, 350)
internal fun mapPower(i: Int) = powerSteps[i]
internal fun mapPowerInverse(power: Int) = powerSteps
    .mapIndexed { index, v -> abs(v - power) to index }
    .minBy { it.first }?.second ?: 0

internal fun getFilters(
    application: Application,
    plugs: LiveData<List<Plug>>,
    networks: LiveData<List<Network>>,
    chargeCards: LiveData<List<ChargeCard>>
): LiveData<List<Filter<FilterValue>>> {
    return MediatorLiveData<List<Filter<FilterValue>>>().apply {
        val plugNames = mapOf(
            Chargepoint.TYPE_1 to application.getString(R.string.plug_type_1),
            Chargepoint.TYPE_2 to application.getString(R.string.plug_type_2),
            Chargepoint.TYPE_3 to application.getString(R.string.plug_type_3),
            Chargepoint.CCS to application.getString(R.string.plug_ccs),
            Chargepoint.SCHUKO to application.getString(R.string.plug_schuko),
            Chargepoint.CHADEMO to application.getString(R.string.plug_chademo),
            Chargepoint.SUPERCHARGER to application.getString(R.string.plug_supercharger),
            Chargepoint.CEE_BLAU to application.getString(R.string.plug_cee_blau),
            Chargepoint.CEE_ROT to application.getString(R.string.plug_cee_rot)
        )
        listOf(plugs, networks, chargeCards).forEach { source ->
            addSource(source) { _ ->
                buildFilters(plugs, plugNames, networks, chargeCards, application)
            }
        }
    }
}

private fun MediatorLiveData<List<Filter<FilterValue>>>.buildFilters(
    plugs: LiveData<List<Plug>>,
    plugNames: Map<String, String>,
    networks: LiveData<List<Network>>,
    chargeCards: LiveData<List<ChargeCard>>,
    application: Application
) {
    val plugMap = plugs.value?.map { plug ->
        plug.name to (plugNames[plug.name] ?: plug.name)
    }?.toMap() ?: return
    val networkMap = networks.value?.map { it.name to it.name }?.toMap() ?: return
    val chargecardMap = chargeCards.value?.map { it.id.toString() to it.name }?.toMap() ?: return
    value = listOf(
        BooleanFilter(application.getString(R.string.filter_free), "freecharging"),
        BooleanFilter(application.getString(R.string.filter_free_parking), "freeparking"),
        BooleanFilter(application.getString(R.string.filter_open_247), "open_247"),
        SliderFilter(
            application.getString(R.string.filter_min_power), "min_power",
            powerSteps.size - 1,
            mapping = ::mapPower,
            inverseMapping = ::mapPowerInverse,
            unit = "kW"
        ),
        MultipleChoiceFilter(
            application.getString(R.string.filter_connectors), "connectors",
            plugMap,
            commonChoices = setOf(Chargepoint.TYPE_2, Chargepoint.CCS, Chargepoint.CHADEMO)
        ),
        SliderFilter(
            application.getString(R.string.filter_min_connectors),
            "min_connectors",
            10
        ),
        MultipleChoiceFilter(
            application.getString(R.string.filter_networks), "networks",
            networkMap, manyChoices = true
        ),
        BooleanFilter(application.getString(R.string.filter_barrierfree), "barrierfree"),
        MultipleChoiceFilter(
            application.getString(R.string.filter_chargecards), "chargecards",
            chargecardMap, manyChoices = true
        )
    )
}


internal fun filtersWithValue(
    filters: LiveData<List<Filter<FilterValue>>>,
    filterValues: LiveData<List<FilterValue>>,
    active: LiveData<Boolean>? = null
): MediatorLiveData<List<FilterWithValue<out FilterValue>>> =
    MediatorLiveData<List<FilterWithValue<out FilterValue>>>().apply {
        listOf(filters, filterValues, active).forEach {
            if (it == null) return@forEach
            addSource(it) {
                val filters = filters.value ?: return@addSource
                value = if (active != null && !active.value!!) {
                    filters.map { filter ->
                        FilterWithValue(filter, filter.defaultValue())
                    }
                } else {
                    val values = filterValues.value ?: return@addSource
                    filters.map { filter ->
                        val value =
                            values.find { it.key == filter.key } ?: filter.defaultValue()
                        FilterWithValue(filter, filter.valueClass.cast(value))
                    }
                }
            }
        }
    }

class FilterViewModel(application: Application, geApiKey: String) :
    AndroidViewModel(application) {
    private var api = GoingElectricApi.create(geApiKey, context = application)
    private var db = AppDatabase.getInstance(application)
    private var prefs = PreferenceDataSource(application)

    private val plugs: LiveData<List<Plug>> by lazy {
        PlugRepository(api, viewModelScope, db.plugDao(), prefs).getPlugs()
    }
    private val networks: LiveData<List<Network>> by lazy {
        NetworkRepository(api, viewModelScope, db.networkDao(), prefs).getNetworks()
    }
    private val chargeCards: LiveData<List<ChargeCard>> by lazy {
        ChargeCardRepository(api, viewModelScope, db.chargeCardDao(), prefs).getChargeCards()
    }
    private val filters: LiveData<List<Filter<FilterValue>>> by lazy {
        getFilters(application, plugs, networks, chargeCards)
    }

    private val filterValues: LiveData<List<FilterValue>> by lazy {
        db.filterValueDao().getFilterValues()
    }

    val filtersWithValue: LiveData<List<FilterWithValue<out FilterValue>>> by lazy {
        filtersWithValue(filters, filterValues)
    }

    suspend fun saveFilterValues() {
        filtersWithValue.value?.forEach {
            db.filterValueDao().insert(it.value)
        }
    }
}

sealed class Filter<out T : FilterValue> : Equatable {
    abstract val name: String
    abstract val key: String
    abstract val valueClass: KClass<out T>
    abstract fun defaultValue(): T
}

data class BooleanFilter(override val name: String, override val key: String) :
    Filter<BooleanFilterValue>() {
    override val valueClass: KClass<BooleanFilterValue> = BooleanFilterValue::class
    override fun defaultValue() = BooleanFilterValue(key, false)
}

data class MultipleChoiceFilter(
    override val name: String,
    override val key: String,
    val choices: Map<String, String>,
    val commonChoices: Set<String>? = null,
    val manyChoices: Boolean = false
) : Filter<MultipleChoiceFilterValue>() {
    override val valueClass: KClass<MultipleChoiceFilterValue> = MultipleChoiceFilterValue::class
    override fun defaultValue() = MultipleChoiceFilterValue(key, mutableSetOf(), true)
}

data class SliderFilter(
    override val name: String,
    override val key: String,
    val max: Int,
    val mapping: ((Int) -> Int) = { it },
    val inverseMapping: ((Int) -> Int) = { it },
    val unit: String? = ""
) : Filter<SliderFilterValue>() {
    override val valueClass: KClass<SliderFilterValue> = SliderFilterValue::class
    override fun defaultValue() = SliderFilterValue(key, 0)
}

sealed class FilterValue : BaseObservable(), Equatable {
    abstract val key: String
}

@Entity
data class BooleanFilterValue(
    @PrimaryKey override val key: String,
    var value: Boolean
) : FilterValue()

@Entity
data class MultipleChoiceFilterValue(
    @PrimaryKey override val key: String,
    var values: MutableSet<String>,
    var all: Boolean
) : FilterValue() {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MultipleChoiceFilterValue) return false
        if (key != other.key) return false

        return if (all) {
            other.all
        } else {
            !other.all && values == other.values
        }
    }
}

@Entity
data class SliderFilterValue(
    @PrimaryKey override val key: String,
    var value: Int
) : FilterValue()

data class FilterWithValue<T : FilterValue>(val filter: Filter<T>, val value: T) : Equatable