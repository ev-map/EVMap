package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.openchargemap.*
import net.vonforst.evmap.api.openstreetmap.OSMReferenceData
import net.vonforst.evmap.viewmodel.Status
import java.time.Duration
import java.time.Instant

@Entity
data class OSMNetwork(@PrimaryKey val name: String)

@Dao
abstract class OSMReferenceDataDao {
    // NETWORKS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg networks: OSMNetwork)

    @Query("DELETE FROM osmnetwork")
    abstract fun deleteAllNetworks()

    @Transaction
    open suspend fun updateNetworks(networks: List<OSMNetwork>) {
        deleteAllNetworks()
        for (network in networks) {
            insert(network)
        }
    }

    @Query("SELECT * FROM osmnetwork")
    abstract fun getAllNetworks(): LiveData<List<OSMNetwork>>
}

class OSMReferenceDataRepository(private val dao: OSMReferenceDataDao) {
    fun getReferenceData(): LiveData<OSMReferenceData> {
        val networks = dao.getAllNetworks()
        return MediatorLiveData<OSMReferenceData>().apply {
            value = null
            addSource(networks) { _ ->
                val n = networks.value ?: return@addSource
                value = OSMReferenceData(n.map { it.name })
            }
        }
    }

    suspend fun updateReferenceData(refData: OSMReferenceData) {
        dao.updateNetworks(refData.networks.map { OSMNetwork(it) })
    }
}