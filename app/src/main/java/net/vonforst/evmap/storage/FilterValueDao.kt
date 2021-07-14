package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*
import net.vonforst.evmap.model.*

@Dao
abstract class FilterValueDao {
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

    open fun getFilterValues(filterStatus: Long, dataSource: String): LiveData<List<FilterValue>> =
        if (filterStatus == FILTERS_DISABLED) {
            MutableLiveData(emptyList())
        } else {
            MediatorLiveData<List<FilterValue>>().apply {
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

}