package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import net.vonforst.evmap.model.ChargeLocation

@Dao
interface ChargeLocationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg locations: ChargeLocation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBlocking(vararg locations: ChargeLocation)

    @Delete
    suspend fun delete(vararg locations: ChargeLocation)

    @Query("SELECT * FROM chargelocation")
    fun getAllChargeLocations(): LiveData<List<ChargeLocation>>

    @Query("SELECT * FROM chargelocation")
    suspend fun getAllChargeLocationsAsync(): List<ChargeLocation>

    @Query("SELECT * FROM chargelocation")
    fun getAllChargeLocationsBlocking(): List<ChargeLocation>

    @Query("SELECT * FROM chargelocation WHERE lat >= :lat1 AND lat <= :lat2 AND lng >= :lng1 AND lng <= :lng2")
    suspend fun getChargeLocationsInBoundsAsync(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double
    ): List<ChargeLocation>
}