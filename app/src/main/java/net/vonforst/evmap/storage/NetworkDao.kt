package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import java.io.IOException
import java.time.Duration
import java.time.Instant

@Entity
data class Network(@PrimaryKey val name: String)

@Dao
interface NetworkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg networks: Network)

    @Delete
    suspend fun delete(vararg networks: Network)

    @Query("SELECT * FROM network")
    fun getAllNetworks(): LiveData<List<Network>>
}

class NetworkRepository(
    private val api: GoingElectricApi, private val scope: CoroutineScope,
    private val dao: NetworkDao, private val prefs: PreferenceDataSource
) {
    fun getNetworks(): LiveData<List<Network>> {
        scope.launch {
            updateNetworks()
        }
        return dao.getAllNetworks()
    }

    private suspend fun updateNetworks() {
        if (Duration.between(prefs.lastNetworkUpdate, Instant.now()) < Duration.ofDays(1)) return

        try {
            val response = api.getNetworks()
            if (!response.isSuccessful) return

            for (name in response.body()!!.result) {
                dao.insert(Network(name))
            }

            prefs.lastNetworkUpdate = Instant.now()
        } catch (e: IOException) {
            // ignore, and retry next time
            e.printStackTrace()
            return
        }
    }
}