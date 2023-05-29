package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*
import net.vonforst.evmap.model.*

@Dao
abstract class FilterValueDao {
    @Query("SELECT * FROM booleanfiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun getBooleanFilterValuesAsync(
        profile: Long,
        dataSource: String
    ): List<BooleanFilterValue>

    @Query("SELECT * FROM multiplechoicefiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun getMultipleChoiceFilterValuesAsync(
        profile: Long,
        dataSource: String
    ): List<MultipleChoiceFilterValue>

    @Query("SELECT * FROM sliderfiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun getSliderFilterValuesAsync(
        profile: Long,
        dataSource: String
    ): List<SliderFilterValue>

    @Query("SELECT * FROM booleanfiltervalue")
    protected abstract suspend fun getAllBooleanFilterValuesAsync(): List<BooleanFilterValue>

    @Query("SELECT * FROM multiplechoicefiltervalue")
    protected abstract suspend fun getAllMultipleChoiceFilterValuesAsync(): List<MultipleChoiceFilterValue>

    @Query("SELECT * FROM sliderfiltervalue")
    protected abstract suspend fun getAllSliderFilterValuesAsync(): List<SliderFilterValue>

    @Query("SELECT * FROM booleanfiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract fun getBooleanFilterValues(
        profile: Long,
        dataSource: String
    ): LiveData<List<BooleanFilterValue>>

    @Query("SELECT * FROM multiplechoicefiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract fun getMultipleChoiceFilterValues(
        profile: Long,
        dataSource: String
    ): LiveData<List<MultipleChoiceFilterValue>>

    @Query("SELECT * FROM sliderfiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract fun getSliderFilterValues(
        profile: Long,
        dataSource: String
    ): LiveData<List<SliderFilterValue>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(vararg values: BooleanFilterValue)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(vararg values: MultipleChoiceFilterValue)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(vararg values: SliderFilterValue)

    @Query("DELETE FROM booleanfiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun deleteBooleanFilterValuesForProfile(
        profile: Long,
        dataSource: String
    )

    @Query("DELETE FROM multiplechoicefiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun deleteMultipleChoiceFilterValuesForProfile(
        profile: Long,
        dataSource: String
    )

    @Query("DELETE FROM sliderfiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun deleteSliderFilterValuesForProfile(
        profile: Long,
        dataSource: String
    )

    open suspend fun getFilterValuesAsync(
        filterStatus: Long,
        dataSource: String
    ): List<FilterValue> =
        if (filterStatus == FILTERS_DISABLED || filterStatus == FILTERS_FAVORITES) {
            emptyList()
        } else {
            getBooleanFilterValuesAsync(filterStatus, dataSource) +
                    getMultipleChoiceFilterValuesAsync(filterStatus, dataSource) +
                    getSliderFilterValuesAsync(filterStatus, dataSource)
        }

    open fun getFilterValues(filterStatus: Long, dataSource: String): LiveData<List<FilterValue>?> =
        if (filterStatus == FILTERS_DISABLED || filterStatus == FILTERS_FAVORITES) {
            MutableLiveData(emptyList())
        } else {
            MediatorLiveData<List<FilterValue>?>().apply {
                value = null
                val sources = listOf(
                    getBooleanFilterValues(filterStatus, dataSource),
                    getMultipleChoiceFilterValues(filterStatus, dataSource),
                    getSliderFilterValues(filterStatus, dataSource)
                )
                for (source in sources) {
                    addSource(source) {
                        val values = sources.map { it.value }
                        if (values.all { it != null }) {
                            value = values.filterNotNull().flatten()
                        }
                    }
                }
            }
        }

    open suspend fun getAllFilterValues(): List<FilterValue> =
        getAllBooleanFilterValuesAsync() +
                getAllMultipleChoiceFilterValuesAsync() +
                getAllSliderFilterValuesAsync()

    @Transaction
    open suspend fun insert(vararg values: FilterValue) {
        values.forEach {
            when (it) {
                is BooleanFilterValue -> insert(it)
                is MultipleChoiceFilterValue -> insert(it)
                is SliderFilterValue -> insert(it)
            }
        }
    }

    @Transaction
    open suspend fun deleteFilterValuesForProfile(profile: Long, dataSource: String) {
        deleteBooleanFilterValuesForProfile(profile, dataSource)
        deleteMultipleChoiceFilterValuesForProfile(profile, dataSource)
        deleteSliderFilterValuesForProfile(profile, dataSource)
    }

    @Transaction
    open suspend fun copyFiltersToCustom(filterStatus: Long, dataSource: String) {
        if (filterStatus == FILTERS_CUSTOM) return

        deleteFilterValuesForProfile(FILTERS_CUSTOM, dataSource)
        val values = getFilterValuesAsync(filterStatus, dataSource).onEach {
            it.profile = FILTERS_CUSTOM
        }
        insert(*values.toTypedArray())
    }

}