package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
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

class FilterViewModel(application: Application, geApiKey: String) :
    AndroidViewModel(application) {
    private var api = GoingElectricApiWrapper(geApiKey, context = application)
    private var db = AppDatabase.getInstance(application)
    private var prefs = PreferenceDataSource(application)

    private val referenceData: LiveData<out ReferenceData> by lazy {
        GEReferenceDataRepository(
            api,
            viewModelScope,
            db.geReferenceDataDao(),
            prefs
        ).getReferenceData()
    }
    private val filters = MediatorLiveData<List<Filter<FilterValue>>>().apply {
        addSource(referenceData) { data ->
            value = api.getFilters(data as GEReferenceData, application.stringProvider())
        }
    }

    private val filterValues: LiveData<List<FilterValue>> by lazy {
        db.filterValueDao().getFilterValues(FILTERS_CUSTOM)
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
                        value = db.filterProfileDao().getProfileById(id)
                    }
                }
            }
        }
    }

    suspend fun saveFilterValues() {
        filtersWithValue.value?.forEach {
            val value = it.value
            value.profile = FILTERS_CUSTOM
            db.filterValueDao().insert(value)
        }

        // set selected profile
        prefs.filterStatus = FILTERS_CUSTOM
    }

    suspend fun saveAsProfile(name: String) {
        // get or create profile
        var profileId = db.filterProfileDao().getProfileByName(name)?.id
        if (profileId == null) {
            profileId = db.filterProfileDao().insert(FilterProfile(name))
        }

        // save filter values
        filtersWithValue.value?.forEach {
            val value = it.value
            value.profile = profileId
            db.filterValueDao().insert(value)
        }

        // set selected profile
        prefs.filterStatus = profileId
    }
}