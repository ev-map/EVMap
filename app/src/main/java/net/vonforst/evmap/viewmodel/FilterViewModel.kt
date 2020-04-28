package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.vonforst.evmap.R
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import net.vonforst.evmap.storage.AppDatabase
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

class FilterViewModel(application: Application, geApiKey: String) :
    AndroidViewModel(application) {
    private var api = GoingElectricApi.create(geApiKey)
    private var db = AppDatabase.getInstance(application)

    private val filters: MutableLiveData<List<Filter<out FilterValue>>> by lazy {
        MutableLiveData<List<Filter<out FilterValue>>>().apply {
            value = listOf(
                BooleanFilter(application.getString(R.string.filter_free), "freecharging")
            )
        }
    }

    private val filterValues: LiveData<List<FilterValue>> by lazy {
        db.filterValueDao().getFilterValues()
    }

    val filtersWithValue: LiveData<List<FilterWithValue<out FilterValue>>> by lazy {
        MediatorLiveData<List<FilterWithValue<out FilterValue>>>().apply {
            for (source in listOf(filters, filterValues)) {
                addSource(source) {
                    val filters = filters.value
                    val values = filterValues.value
                    if (filters != null && values != null) {
                        value = filters.map { filter ->
                            val value =
                                values.find { it.key == filter.key } ?: filter.defaultValue()
                            FilterWithValue(filter, filter.valueClass.cast(value))
                        }
                    } else {
                        value = null
                    }
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
    override fun defaultValue() = MultipleChoiceFilterValue(key, emptySet(), true)
}

sealed class FilterValue : Equatable {
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
    val values: Set<String>,
    val all: Boolean
) : FilterValue()

data class FilterWithValue<T : FilterValue>(val filter: Filter<T>, val value: T) : Equatable