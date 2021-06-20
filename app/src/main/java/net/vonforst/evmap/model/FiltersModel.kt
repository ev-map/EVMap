package net.vonforst.evmap.model

import androidx.databinding.BaseObservable
import androidx.room.Entity
import androidx.room.ForeignKey
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.storage.FilterProfile
import kotlin.reflect.KClass

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
        onDelete = ForeignKey.CASCADE
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
        onDelete = ForeignKey.CASCADE
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
        onDelete = ForeignKey.CASCADE
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
    (this.find { it.value.key == key }?.value as BooleanFilterValue?)?.value

fun FilterValues.getSliderValue(key: String) =
    (this.find { it.value.key == key }?.value as SliderFilterValue?)?.value

fun FilterValues.getMultipleChoiceFilter(key: String) =
    this.find { it.value.key == key }?.filter as MultipleChoiceFilter?

fun FilterValues.getMultipleChoiceValue(key: String) =
    this.find { it.value.key == key }?.value as MultipleChoiceFilterValue?

const val FILTERS_DISABLED = -2L
const val FILTERS_CUSTOM = -1L