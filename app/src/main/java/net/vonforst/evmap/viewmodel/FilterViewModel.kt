package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.*
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.ChargeLocationsRepository
import net.vonforst.evmap.storage.FilterProfile
import net.vonforst.evmap.storage.PreferenceDataSource


class FilterViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferenceDataSource(application)
    private val api: ChargepointApi<ReferenceData> = createApi(prefs.dataSource, application)
    private val repo = ChargeLocationsRepository(api, viewModelScope, db, prefs)
    private val filters = repo.getFilters(application.stringProvider())

    private val filterValues: LiveData<List<FilterValue>?> by lazy {
        db.filterValueDao().getFilterValues(FILTERS_CUSTOM, prefs.dataSource)
    }

    val filtersWithValue: LiveData<FilterValues?> by lazy {
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
                    FILTERS_CUSTOM, FILTERS_DISABLED, FILTERS_FAVORITES -> value = null
                    else -> viewModelScope.launch {
                        value = db.filterProfileDao().getProfileById(id, prefs.dataSource)
                    }
                }
            }
        }
    }

    suspend fun saveFilterValues() {
        filtersWithValue.value?.map {
            val value = it.value
            value.profile = FILTERS_CUSTOM
            value.dataSource = prefs.dataSource
            value
        }?.let {
            db.filterValueDao().insert(*it.toTypedArray())
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
        filtersWithValue.value?.map {
            val value = it.value
            value.profile = profileId
            value.dataSource = prefs.dataSource
            value
        }?.let {
            db.filterValueDao().insert(*it.toTypedArray())
        }

        // set selected profile
        prefs.filterStatus = profileId
    }

    suspend fun deleteCurrentProfile() {
        filterProfile.value?.let {
            db.filterProfileDao().delete(it)
            prefs.filterStatus = FILTERS_DISABLED
        }
    }
}