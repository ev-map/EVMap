package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.model.FILTERS_DISABLED
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.FilterProfile
import net.vonforst.evmap.storage.PreferenceDataSource

class FilterProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private var db = AppDatabase.getInstance(application)
    private var prefs = PreferenceDataSource(application)

    val filterProfiles: LiveData<List<FilterProfile>> by lazy {
        db.filterProfileDao().getProfiles()
    }

    fun delete(itemId: Long) {
        viewModelScope.launch {
            val profile = db.filterProfileDao().getProfileById(itemId)
            profile?.let { db.filterProfileDao().delete(it) }
            if (prefs.filterStatus == profile?.id) {
                prefs.filterStatus = FILTERS_DISABLED
            }
        }
    }

    fun insert(item: FilterProfile) {
        viewModelScope.launch {
            db.filterProfileDao().insert(item)
        }
    }

    fun update(item: FilterProfile) {
        viewModelScope.launch {
            db.filterProfileDao().update(item)
        }
    }

    fun reorderProfiles(list: List<FilterProfile>) {
        viewModelScope.launch {
            db.filterProfileDao().update(*list.toTypedArray())
        }
    }
}