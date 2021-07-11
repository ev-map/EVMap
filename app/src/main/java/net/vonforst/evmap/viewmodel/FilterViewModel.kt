package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.*
import kotlin.reflect.full.cast

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

class FilterViewModel(application: Application) : AndroidViewModel(application) {
    private var db = AppDatabase.getInstance(application)
    private var prefs = PreferenceDataSource(application)
    private var api: ChargepointApi<ReferenceData> = createApi(prefs.dataSource, application)

    private val referenceData: LiveData<out ReferenceData> by lazy {
        val api = api
        when (api) {
            is GoingElectricApiWrapper -> {
                GEReferenceDataRepository(
                    api,
                    viewModelScope,
                    db.geReferenceDataDao(),
                    prefs
                ).getReferenceData()
            }
            is OpenChargeMapApiWrapper -> {
                OCMReferenceDataRepository(
                    api,
                    viewModelScope,
                    db.ocmReferenceDataDao(),
                    prefs
                ).getReferenceData()
            }
            else -> {
                throw RuntimeException("no reference data implemented")
            }
        }
    }
    private val filters = MediatorLiveData<List<Filter<FilterValue>>>().apply {
        addSource(referenceData) { data ->
            value = api.getFilters(data, application.stringProvider())
        }
    }

    private val filterValues: LiveData<List<FilterValue>> by lazy {
        db.filterValueDao().getFilterValues(FILTERS_CUSTOM, prefs.dataSource)
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
                        value = db.filterProfileDao().getProfileById(id, prefs.dataSource)
                    }
                }
            }
        }
    }

    suspend fun saveFilterValues() {
        filtersWithValue.value?.forEach {
            val value = it.value
            value.profile = FILTERS_CUSTOM
            value.dataSource = prefs.dataSource
            db.filterValueDao().insert(value)
        }

        // set selected profile
        prefs.filterStatus = FILTERS_CUSTOM
    }

    suspend fun saveAsProfile(name: String) {
        // get or create profile
        var profileId = db.filterProfileDao().getProfileByName(name, prefs.dataSource)?.id
        if (profileId == null) {
            profileId = db.filterProfileDao().getNewId(prefs.dataSource)
            db.filterProfileDao().insert(FilterProfile(name, prefs.dataSource, profileId))
        }

        // save filter values
        filtersWithValue.value?.forEach {
            val value = it.value
            value.profile = profileId
            value.dataSource = prefs.dataSource
            db.filterValueDao().insert(value)
        }

        // set selected profile
        prefs.filterStatus = profileId
    }
}