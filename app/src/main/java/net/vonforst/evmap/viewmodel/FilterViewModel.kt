package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.databinding.BaseObservable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.api.goingelectric.Chargepoint
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.storage.AppDatabase
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

val powerSteps = listOf(0, 2, 3, 7, 11, 22, 43, 50, 100, 150, 200, 250, 300, 350)
internal fun mapPower(i: Int) = powerSteps[i]
internal fun mapPowerInverse(power: Int) = powerSteps
    .mapIndexed { index, v -> abs(v - power) to index }
    .minBy { it.first }?.second ?: 0

fun getFilters(application: Application): List<Filter<FilterValue>> {
    return listOf(
        BooleanFilter(application.getString(R.string.filter_free), "freecharging"),
        BooleanFilter(application.getString(R.string.filter_free_parking), "freeparking"),
        SliderFilter(
            application.getString(R.string.filter_min_power), "min_power",
            powerSteps.size - 1,
            mapping = ::mapPower,
            inverseMapping = ::mapPowerInverse,
            unit = "kW"
        ),
        MultipleChoiceFilter(
            application.getString(R.string.filter_connectors), "connectors",
            mapOf(
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
        ),
        SliderFilter(
            application.getString(R.string.filter_min_connectors),
            "min_connectors",
            10
        )
    )
}

class FilterViewModel(application: Application, geApiKey: String) :
    AndroidViewModel(application) {
    private var api = GoingElectricApi.create(geApiKey, context = application)
    private var db = AppDatabase.getInstance(application)

    private val filters = getFilters(application)

    private val filterValues: LiveData<List<FilterValue>> by lazy {
        db.filterValueDao().getFilterValues()
    }

    val filtersWithValue: LiveData<List<FilterWithValue<out FilterValue>>> by lazy {
        MediatorLiveData<List<FilterWithValue<out FilterValue>>>().apply {
            addSource(filterValues) { values ->
                value = if (values != null) {
                    filters.map { filter ->
                        val value =
                            values.find { it.key == filter.key } ?: filter.defaultValue()
                        FilterWithValue(filter, filter.valueClass.cast(value))
                    }
                } else {
                    null
                }
            }
        }
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
    val choices: Map<String, String>
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
) : FilterValue()

@Entity
data class SliderFilterValue(
    @PrimaryKey override val key: String,
    var value: Int
) : FilterValue()

data class FilterWithValue<T : FilterValue>(val filter: Filter<T>, val value: T) : Equatable