package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.nobil.*
import net.vonforst.evmap.viewmodel.Status
import java.time.Duration
import java.time.Instant

@Dao
abstract class NobilReferenceDataDao {
}

class NobilReferenceDataRepository(
    private val scope: CoroutineScope,
    private val prefs: PreferenceDataSource
) {
    fun getReferenceData(): LiveData<NobilReferenceData> {
        return MediatorLiveData<NobilReferenceData>().apply {
            value = NobilReferenceData(0)
        }
    }
}