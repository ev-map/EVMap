package net.vonforst.evmap.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.openchargemap.OCMConnectionType
import net.vonforst.evmap.api.openchargemap.OCMCountry
import net.vonforst.evmap.api.openchargemap.OCMOperator
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
    abstract fun getAllConnectionTypes(): Flow<List<OCMConnectionType>>

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
    abstract fun getAllCountries(): Flow<List<OCMCountry>>

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
    abstract fun getAllOperators(): Flow<List<OCMOperator>>
}

class OCMReferenceDataRepository(
    private val api: OpenChargeMapApiWrapper, private val scope: CoroutineScope,
    private val dao: OCMReferenceDataDao, private val prefs: PreferenceDataSource
) {
    fun getReferenceData(): Flow<OCMReferenceData> {
        scope.launch {
            updateData()
        }
        val connectionTypes = dao.getAllConnectionTypes()
        val countries = dao.getAllCountries()
        val operators = dao.getAllOperators()
        return combine(connectionTypes, countries, operators) { ct, c, o -> OCMReferenceData(ct, c, o) }
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