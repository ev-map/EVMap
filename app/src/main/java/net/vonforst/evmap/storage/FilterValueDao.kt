package net.vonforst.evmap.storage

import androidx.lifecycle.liveData
import androidx.room.*
import net.vonforst.evmap.model.*

@Dao
abstract class FilterValueDao {
    @Query("SELECT * FROM booleanfiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun getBooleanFilterValues(
        profile: Long,
        dataSource: String
    ): List<BooleanFilterValue>

    @Query("SELECT * FROM multiplechoicefiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun getMultipleChoiceFilterValues(
        profile: Long,
        dataSource: String
    ): List<MultipleChoiceFilterValue>

    @Query("SELECT * FROM sliderfiltervalue WHERE profile = :profile AND dataSource = :dataSource")
    protected abstract suspend fun getSliderFilterValues(
        profile: Long,
        dataSource: String
    ): List<SliderFilterValue>

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
            getBooleanFilterValues(filterStatus, dataSource) +
                    getMultipleChoiceFilterValues(filterStatus, dataSource) +
                    getSliderFilterValues(filterStatus, dataSource)
        }

    open fun getFilterValues(filterStatus: Long, dataSource: String) = liveData {
        emit(null)
        emit(getFilterValuesAsync(filterStatus, dataSource))
    }

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