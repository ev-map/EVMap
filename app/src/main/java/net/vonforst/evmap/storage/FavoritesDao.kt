package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import net.vonforst.evmap.model.Favorite
import net.vonforst.evmap.model.FavoriteWithDetail

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg favorites: Favorite)

    @Delete
    suspend fun delete(vararg favorites: Favorite)

    @Query("SELECT * FROM favorite LEFT JOIN chargelocation ON favorite.chargerDataSource = chargelocation.dataSource AND favorite.chargerId = chargelocation.id")
    fun getAllFavorites(): LiveData<List<FavoriteWithDetail>>

    @Query("SELECT * FROM favorite LEFT JOIN chargelocation ON favorite.chargerDataSource = chargelocation.dataSource AND favorite.chargerId = chargelocation.id")
    suspend fun getAllFavoritesAsync(): List<FavoriteWithDetail>

    @Query("SELECT * FROM favorite LEFT JOIN chargelocation ON favorite.chargerDataSource = chargelocation.dataSource AND favorite.chargerId = chargelocation.id WHERE lat >= :lat1 AND lat <= :lat2 AND lng >= :lng1 AND lng <= :lng2")
    suspend fun getFavoritesInBoundsAsync(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double
    ): List<FavoriteWithDetail>
}