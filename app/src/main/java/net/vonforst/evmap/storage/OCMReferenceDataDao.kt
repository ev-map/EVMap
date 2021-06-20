package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.openchargemap.OCMConnectionType
import net.vonforst.evmap.api.openchargemap.OCMCountry
import net.vonforst.evmap.api.openchargemap.OCMReferenceData
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.viewmodel.Status
import java.time.Duration
import java.time.Instant

@Dao
abstract class OCMReferenceDataDao {
    // CONNECTION TYPES
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg connectionTypes: OCMConnectionType)

    @Query("DELETE FROM ocmconnectiontype")
    abstract fun deleteAllConnectionTypes()

    @Transaction
    open suspend fun updateConnectionTypes(connectionTypes: List<OCMConnectionType>) {
        deleteAllConnectionTypes()
        for (connectionType in connectionTypes) {
            insert(connectionType)
        }
    }

    @Query("SELECT * FROM ocmconnectiontype")
    abstract fun getAllConnectionTypes(): LiveData<List<OCMConnectionType>>

    // COUNTRIES
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg countries: OCMCountry)

    @Query("DELETE FROM ocmcountry")
    abstract fun deleteAllCountries()

    @Transaction
    open suspend fun updateCountries(countries: List<OCMCountry>) {
        deleteAllCountries()
        for (country in countries) {
            insert(country)
        }
    }

    @Query("SELECT * FROM ocmcountry")
    abstract fun getAllCountries(): LiveData<List<OCMCountry>>
}

class OCMReferenceDataRepository(
    private val api: OpenChargeMapApiWrapper, private val scope: CoroutineScope,
    private val dao: OCMReferenceDataDao, private val prefs: PreferenceDataSource
) {
    fun getReferenceData(): LiveData<OCMReferenceData> {
        scope.launch {
            updateData()
        }
        val connectionTypes = dao.getAllConnectionTypes()
        val countries = dao.getAllCountries()
        return MediatorLiveData<OCMReferenceData>().apply {
            listOf(countries, connectionTypes).map { source ->
                addSource(source) { _ ->
                    val ct = connectionTypes.value
                    val c = countries.value
                    if (ct.isNullOrEmpty() || c.isNullOrEmpty()) return@addSource
                    value = OCMReferenceData(ct, c)
                }
            }
        }
    }

    private suspend fun updateData() {
        if (Duration.between(
                prefs.lastOcmReferenceDataUpdate,
                Instant.now()
            ) < Duration.ofDays(1)
        ) return

        val response = api.getReferenceData()
        if (response.status == Status.ERROR) return  // ignore and retry next time


        val data = response.data!!
        dao.updateConnectionTypes(data.connectionTypes)
        dao.updateCountries(data.countries)

        prefs.lastOcmReferenceDataUpdate = Instant.now()
    }
}