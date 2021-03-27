package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.databinding.BaseObservable
import androidx.lifecycle.*
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import kotlinx.coroutines.launch
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
    .minByOrNull { it.first }?.second ?: 0

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
            Chargepoint.CEE_ROT to application.getString(R.string.plug_cee_rot),
            Chargepoint.TESLA_ROADSTER_HPC to application.getString(R.string.plug_roadster_hpc)
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
    val categoryMap = mapOf(
        "Autohaus" to application.getString(R.string.category_car_dealership),
        "Autobahnraststätte" to application.getString(R.string.category_service_on_motorway),
        "Autohof" to application.getString(R.string.category_service_off_motorway),
        "Bahnhof" to application.getString(R.string.category_railway_station),
        "Behörde" to application.getString(R.string.category_public_authorities),
        "Campingplatz" to application.getString(R.string.category_camping),
        "Einkaufszentrum" to application.getString(R.string.category_shopping_mall),
        "Ferienwohnung" to application.getString(R.string.category_holiday_home),
        "Flughafen" to application.getString(R.string.category_airport),
        "Freizeitpark" to application.getString(R.string.category_amusement_park),
        "Hotel" to application.getString(R.string.category_hotel),
        "Kino" to application.getString(R.string.category_cinema),
        "Kirche" to application.getString(R.string.category_church),
        "Krankenhaus" to application.getString(R.string.category_hospital),
        "Museum" to application.getString(R.string.category_museum),
        "Parkhaus" to application.getString(R.string.category_parking_multi),
        "Parkplatz" to application.getString(R.string.category_parking),
        "Privater Ladepunkt" to application.getString(R.string.category_private_charger),
        "Rastplatz" to application.getString(R.string.category_rest_area),
        "Restaurant" to application.getString(R.string.category_restaurant),
        "Schwimmbad" to application.getString(R.string.category_swimming_pool),
        "Supermarkt" to application.getString(R.string.category_supermarket),
        "Tankstelle" to application.getString(R.string.category_petrol_station),
        "Tiefgarage" to application.getString(R.string.category_parking_underground),
        "Tierpark" to application.getString(R.string.category_zoo),
        "Wohnmobilstellplatz" to application.getString(R.string.category_caravan_site)
    )
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
            commonChoices = setOf(Chargepoint.TYPE_2, Chargepoint.CCS, Chargepoint.CHADEMO),
            manyChoices = true
        ),
        SliderFilter(
            application.getString(R.string.filter_min_connectors),
            "min_connectors",
            10,
            min = 1
        ),
        MultipleChoiceFilter(
            application.getString(R.string.filter_networks), "networks",
            networkMap, manyChoices = true
        ),
        MultipleChoiceFilter(
            application.getString(R.string.categories), "categories",
            categoryMap,
            manyChoices = true
        ),
        BooleanFilter(application.getString(R.string.filter_barrierfree), "barrierfree"),
        MultipleChoiceFilter(
            application.getString(R.string.filter_chargecards), "chargecards",
            chargecardMap, manyChoices = true
        ),
        BooleanFilter(application.getString(R.string.filter_exclude_faults), "exclude_faults")
    )
}


internal fun filtersWithValue(
    filters: LiveData<List<Filter<FilterValue>>>,
    filterValues: LiveData<List<FilterValue>>
): MediatorLiveData<FilterValues> =
    MediatorLiveData<FilterValues>().apply {
        listOf(filters, filterValues).forEach {
            addSource(it) {
                val f = filters.value ?: return@addSource
                val values = filterValues.value ?: return@addSource
                value = f.map { filter ->
                    val value =
                        values.find { it.key == filter.key } ?: filter.defaultValue()
                    FilterWithValue(filter, filter.valueClass.cast(value))
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
        db.filterValueDao().getFilterValues(FILTERS_CUSTOM)
    }

    val filtersWithValue: LiveData<FilterValues> by lazy {
        filtersWithValue(filters, filterValues)
    }

    private val filterStatus: LiveData<Long> by lazy {
        MutableLiveData<Long>().apply {
            value = prefs.filterStatus
        }
    }

    val filterProfile: LiveData<FilterProfile> by lazy {
        MediatorLiveData<FilterProfile>().apply {
            addSource(filterStatus) { id ->
                when (id) {
                    FILTERS_CUSTOM, FILTERS_DISABLED -> value = null
                    else -> viewModelScope.launch {
                        value = db.filterProfileDao().getProfileById(id)
                    }
                }
            }
        }
    }

    suspend fun saveFilterValues() {
        filtersWithValue.value?.forEach {
            val value = it.value
            value.profile = FILTERS_CUSTOM
            db.filterValueDao().insert(value)
        }

        // set selected profile
        prefs.filterStatus = FILTERS_CUSTOM
    }

    suspend fun saveAsProfile(name: String) {
        // get or create profile
        var profileId = db.filterProfileDao().getProfileByName(name)?.id
        if (profileId == null) {
            profileId = db.filterProfileDao().insert(FilterProfile(name))
        }

        // save filter values
        filtersWithValue.value?.forEach {
            val value = it.value
            value.profile = profileId
            db.filterValueDao().insert(value)
        }

        // set selected profile
        prefs.filterStatus = profileId
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
    val min: Int = 0,
    val mapping: ((Int) -> Int) = { it },
    val inverseMapping: ((Int) -> Int) = { it },
    val unit: String? = ""
) : Filter<SliderFilterValue>() {
    override val valueClass: KClass<SliderFilterValue> = SliderFilterValue::class
    override fun defaultValue() = SliderFilterValue(key, min)
}

sealed class FilterValue : BaseObservable(), Equatable {
    abstract val key: String
    var profile: Long = FILTERS_CUSTOM
}

@Entity(
    foreignKeys = [ForeignKey(
        entity = FilterProfile::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("profile"),
        onDelete = CASCADE
    )],
    primaryKeys = ["key", "profile"]
)
data class BooleanFilterValue(
    override val key: String,
    var value: Boolean
) : FilterValue()

@Entity(
    foreignKeys = [ForeignKey(
        entity = FilterProfile::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("profile"),
        onDelete = CASCADE
    )],
    primaryKeys = ["key", "profile"]
)
data class MultipleChoiceFilterValue(
    override val key: String,
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

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + all.hashCode()
        result = 31 * result + if (all) 0 else values.hashCode()
        return result
    }
}

@Entity(
    foreignKeys = [ForeignKey(
        entity = FilterProfile::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("profile"),
        onDelete = CASCADE
    )],
    primaryKeys = ["key", "profile"]
)
data class SliderFilterValue(
    override val key: String,
    var value: Int
) : FilterValue()

data class FilterWithValue<T : FilterValue>(val filter: Filter<T>, val value: T) : Equatable

typealias FilterValues = List<FilterWithValue<out FilterValue>>

fun FilterValues.getBooleanValue(key: String) =
    (this.find { it.value.key == key }!!.value as BooleanFilterValue).value

fun FilterValues.getSliderValue(key: String) =
    (this.find { it.value.key == key }!!.value as SliderFilterValue).value

fun FilterValues.getMultipleChoiceFilter(key: String) =
    this.find { it.value.key == key }!!.filter as MultipleChoiceFilter

fun FilterValues.getMultipleChoiceValue(key: String) =
    this.find { it.value.key == key }!!.value as MultipleChoiceFilterValue

const val FILTERS_DISABLED = -2L
const val FILTERS_CUSTOM = -1L