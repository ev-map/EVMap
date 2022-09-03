package net.vonforst.evmap.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.switchMap
import kotlinx.coroutines.CoroutineScope
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.*
import kotlin.reflect.full.cast

fun ChargepointApi<ReferenceData>.getReferenceData(
    scope: CoroutineScope,
    ctx: Context
): LiveData<out ReferenceData> {
    val db = AppDatabase.getInstance(ctx)
    val prefs = PreferenceDataSource(ctx)
    return when (this) {
        is GoingElectricApiWrapper -> {
            GEReferenceDataRepository(
                this,
                scope,
                db.geReferenceDataDao(),
                prefs
            ).getReferenceData()
        }
        is OpenChargeMapApiWrapper -> {
            OCMReferenceDataRepository(
                this,
                scope,
                db.ocmReferenceDataDao(),
                prefs
            ).getReferenceData()
        }
        else -> {
            throw RuntimeException("no reference data implemented")
        }
    }
}

fun filtersWithValue(
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

fun FilterValueDao.getFilterValues(filterStatus: LiveData<Long>, dataSource: String) =
    filterStatus.switchMap {
        getFilterValues(it, dataSource)
    }