package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.room.*
import net.vonforst.evmap.viewmodel.BooleanFilterValue
import net.vonforst.evmap.viewmodel.FilterValue
import net.vonforst.evmap.viewmodel.MultipleChoiceFilterValue

@Dao
abstract class FilterValueDao {
    @Query("SELECT * FROM booleanfiltervalue")
    protected abstract fun getBooleanFilterValues(): LiveData<List<BooleanFilterValue>>

    @Query("SELECT * FROM multiplechoicefiltervalue")
    protected abstract fun getMultipleChoiceFilterValues(): LiveData<List<MultipleChoiceFilterValue>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(vararg values: BooleanFilterValue)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(vararg values: MultipleChoiceFilterValue)

    open fun getFilterValues(): LiveData<List<FilterValue>> =
        MediatorLiveData<List<FilterValue>>().apply {
            val sources = listOf(getBooleanFilterValues(), getMultipleChoiceFilterValues())
            for (source in sources) {
                addSource(source) {
                    value = sources.mapNotNull { it.value }.flatten()
                }
            }
        }

    @Transaction
    open suspend fun insert(vararg values: FilterValue) {
        values.forEach {
            when (it) {
                is BooleanFilterValue -> insert(it)
                is MultipleChoiceFilterValue -> insert(it)
            }
        }
    }
}