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
data class Plug(@PrimaryKey val name: String)

@Dao
interface PlugDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg plugs: Plug)

    @Delete
    suspend fun delete(vararg plugs: Plug)

    @Query("SELECT * FROM plug")
    fun getAllPlugs(): LiveData<List<Plug>>
}

class PlugRepository(
    private val api: GoingElectricApi, private val scope: CoroutineScope,
    private val dao: PlugDao, private val prefs: PreferenceDataSource
) {
    fun getPlugs(): LiveData<List<Plug>> {
        scope.launch {
            updatePlugs()
        }
        return dao.getAllPlugs()
    }

    private suspend fun updatePlugs() {
        if (Duration.between(prefs.lastPlugUpdate, Instant.now()) < Duration.ofDays(1)) return

        try {
            val response = api.getPlugs()
            if (!response.isSuccessful) return

            for (name in response.body()!!.result) {
                dao.insert(Plug(name))
            }

            prefs.lastPlugUpdate = Instant.now()
        } catch (e: IOException) {
            // ignore, and retry next time
            e.printStackTrace()
            return
        }
    }
}