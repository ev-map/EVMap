package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.goingelectric.GEChargeCard
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.viewmodel.Status
import java.time.Duration
import java.time.Instant

@Entity
data class GENetwork(@PrimaryKey val name: String)

@Entity
data class GEPlug(@PrimaryKey val name: String)

@Dao
abstract class GEReferenceDataDao {
    // NETWORKS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg networks: GENetwork)

    @Query("DELETE FROM genetwork")
    abstract fun deleteAllNetworks()

    @Transaction
    open suspend fun updateNetworks(networks: List<GENetwork>) {
        deleteAllNetworks()
        for (network in networks) {
            insert(network)
        }
    }

    @Query("SELECT * FROM genetwork")
    abstract fun getAllNetworks(): LiveData<List<GENetwork>>

    // PLUGS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg plugs: GEPlug)

    @Query("DELETE FROM geplug")
    abstract fun deleteAllPlugs()

    @Transaction
    open suspend fun updatePlugs(plugs: List<GEPlug>) {
        deleteAllPlugs()
        for (plug in plugs) {
            insert(plug)
        }
    }

    @Query("SELECT * FROM geplug")
    abstract fun getAllPlugs(): LiveData<List<GEPlug>>

    // CHARGE CARDS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg chargeCards: GEChargeCard)

    @Query("DELETE FROM gechargecard")
    abstract fun deleteAllChargeCards()

    @Transaction
    open suspend fun updateChargeCards(chargeCards: List<GEChargeCard>) {
        deleteAllChargeCards()
        for (chargeCard in chargeCards) {
            insert(chargeCard)
        }
    }

    @Query("SELECT * FROM gechargecard")
    abstract fun getAllChargeCards(): LiveData<List<GEChargeCard>>
}

class GEReferenceDataRepository(
    private val api: GoingElectricApiWrapper, private val scope: CoroutineScope,
    private val dao: GEReferenceDataDao, private val prefs: PreferenceDataSource
) {
    fun getReferenceData(): LiveData<GEReferenceData> {
        scope.launch {
            updateData()
        }
        val plugs = dao.getAllPlugs()
        val networks = dao.getAllNetworks()
        val chargeCards = dao.getAllChargeCards()
        return MediatorLiveData<GEReferenceData>().apply {
            value = null
            listOf(chargeCards, networks, plugs).map { source ->
                addSource(source) { _ ->
                    val p = plugs.value ?: return@addSource
                    val n = networks.value ?: return@addSource
                    val cc = chargeCards.value ?: return@addSource
                    value = GEReferenceData(p.map { it.name }, n.map { it.name }, cc)
                }
            }
        }
    }

    private suspend fun updateData() {
        if (Duration.between(
                prefs.lastGeReferenceDataUpdate,
                Instant.now()
            ) < Duration.ofDays(1)
        ) return

        val response = api.getReferenceData()
        if (response.status == Status.ERROR) return  // ignore and retry next time


        val data = response.data!!
        dao.updateNetworks(data.networks.map { GENetwork(it) })
        dao.updatePlugs(data.plugs.map { GEPlug(it) })
        dao.updateChargeCards(data.chargecards)

        prefs.lastGeReferenceDataUpdate = Instant.now()
    }
}