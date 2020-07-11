package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.vonforst.evmap.api.goingelectric.ChargeCard
import net.vonforst.evmap.api.goingelectric.GoingElectricApi
import java.io.IOException
import java.time.Duration
import java.time.Instant

@Dao
interface ChargeCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg chargeCards: ChargeCard)

    @Delete
    suspend fun delete(vararg chargeCards: ChargeCard)

    @Query("SELECT * FROM chargeCard")
    fun getAllChargeCards(): LiveData<List<ChargeCard>>
}

class ChargeCardRepository(
    private val api: GoingElectricApi, private val scope: CoroutineScope,
    private val dao: ChargeCardDao, private val prefs: PreferenceDataSource
) {
    fun getChargeCards(): LiveData<List<ChargeCard>> {
        scope.launch {
            updateChargeCards()
        }
        return dao.getAllChargeCards()
    }

    private suspend fun updateChargeCards() {
        if (Duration.between(prefs.lastChargeCardUpdate, Instant.now()) < Duration.ofDays(1)) return

        try {
            val response = api.getChargeCards()
            if (!response.isSuccessful) return

            for (card in response.body()!!.result) {
                dao.insert(card)
            }

            prefs.lastChargeCardUpdate = Instant.now()
        } catch (e: IOException) {
            // ignore, and retry next time
            e.printStackTrace()
            return
        }
    }
}