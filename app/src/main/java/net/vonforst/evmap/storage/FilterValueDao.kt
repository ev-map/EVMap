package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*
import net.vonforst.evmap.model.*

@Dao
abstract class FilterValueDao {
    @Query("SELECT * FROM booleanfiltervalue WHERE profile = :profile")
    protected abstract fun getBooleanFilterValues(profile: Long): LiveData<List<BooleanFilterValue>>

    @Query("SELECT * FROM multiplechoicefiltervalue WHERE profile = :profile")
    protected abstract fun getMultipleChoiceFilterValues(profile: Long): LiveData<List<MultipleChoiceFilterValue>>

    @Query("SELECT * FROM sliderfiltervalue WHERE profile = :profile")
    protected abstract fun getSliderFilterValues(profile: Long): LiveData<List<SliderFilterValue>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(vararg values: BooleanFilterValue)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(vararg values: MultipleChoiceFilterValue)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(vararg values: SliderFilterValue)

    @Query("DELETE FROM booleanfiltervalue WHERE profile = :profile")
    protected abstract suspend fun deleteBooleanFilterValuesForProfile(profile: Long)

    @Query("DELETE FROM multiplechoicefiltervalue WHERE profile = :profile")
    protected abstract suspend fun deleteMultipleChoiceFilterValuesForProfile(profile: Long)

    @Query("DELETE FROM sliderfiltervalue WHERE profile = :profile")
    protected abstract suspend fun deleteSliderFilterValuesForProfile(profile: Long)

    open fun getFilterValues(filterStatus: Long): LiveData<List<FilterValue>> =
        if (filterStatus == FILTERS_DISABLED) {
            MutableLiveData(emptyList())
        } else {
            MediatorLiveData<List<FilterValue>>().apply {
                val sources = listOf(
                    getBooleanFilterValues(filterStatus),
                    getMultipleChoiceFilterValues(filterStatus),
                    getSliderFilterValues(filterStatus)
                )
                for (source in sources) {
                    addSource(source) {
                        value = sources.mapNotNull { it.value }.flatten()
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
    open suspend fun deleteFilterValuesForProfile(profile: Long) {
        deleteBooleanFilterValuesForProfile(profile)
        deleteMultipleChoiceFilterValuesForProfile(profile)
        deleteSliderFilterValuesForProfile(profile)
    }

}