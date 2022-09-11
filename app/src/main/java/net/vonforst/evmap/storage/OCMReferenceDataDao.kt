package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.openchargemap.*
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

    // OPERATORS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg operators: OCMOperator)

    @Query("DELETE FROM ocmoperator")
    abstract fun deleteAllOperators()

    @Transaction
    open suspend fun updateOperators(operators: List<OCMOperator>) {
        deleteAllOperators()
        for (operator in operators) {
            insert(operator)
        }
    }

    @Query("SELECT * FROM ocmoperator")
    abstract fun getAllOperators(): LiveData<List<OCMOperator>>
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
        val operators = dao.getAllOperators()
        return MediatorLiveData<OCMReferenceData>().apply {
            value = null
            listOf(countries, connectionTypes, operators).map { source ->
                addSource(source) { _ ->
                    val ct = connectionTypes.value
                    val c = countries.value
                    val o = operators.value
                    if (ct.isNullOrEmpty() || c.isNullOrEmpty() || o.isNullOrEmpty()) return@addSource
                    value = OCMReferenceData(ct, c, o)
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
        dao.updateOperators(data.operators)

        prefs.lastOcmReferenceDataUpdate = Instant.now()
    }
}