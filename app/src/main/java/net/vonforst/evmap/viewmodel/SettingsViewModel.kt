package net.vonforst.evmap.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.PreferenceDataSource

class SettingsViewModel(
    application: Application,
) :
    AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val prefs = PreferenceDataSource(application)

    val chargerCacheCount: LiveData<Long> by lazy {
        db.chargeLocationsDao().getCount()
    }

    val chargerCacheSize: LiveData<Long> by lazy {
        MutableLiveData<Long>().apply {
            chargerCacheCount.observeForever {
                viewModelScope.launch {
                    value = db.chargeLocationsDao().getSize()
                }
            }
        }
    }

    fun deleteRecentSearchResults() {
        viewModelScope.launch {
            db.recentAutocompletePlaceDao().deleteAll()
        }
    }

    fun clearChargerCache() {
        viewModelScope.launch {
            db.savedRegionDao().deleteAll()
            db.chargeLocationsDao().deleteAllIfNotFavorite()
        }
    }
}