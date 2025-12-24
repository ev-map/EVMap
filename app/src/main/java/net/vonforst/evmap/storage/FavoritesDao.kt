package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import net.vonforst.evmap.model.Favorite
import net.vonforst.evmap.model.FavoriteWithDetail

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg favorites: Favorite): List<Long>

    @Delete
    suspend fun delete(vararg favorites: Favorite)

    @Query("SELECT * FROM favorite LEFT JOIN chargelocation ON favorite.chargerDataSource = chargelocation.dataSource AND favorite.chargerId = chargelocation.id WHERE chargelocation.id is not NULL")
    fun getAllFavorites(): LiveData<List<FavoriteWithDetail>>

    @Query("SELECT * FROM favorite LEFT JOIN chargelocation ON favorite.chargerDataSource = chargelocation.dataSource AND favorite.chargerId = chargelocation.id WHERE chargelocation.id is not NULL")
    suspend fun getAllFavoritesAsync(): List<FavoriteWithDetail>

    @SkipQueryVerification
    @Query("SELECT * FROM favorite LEFT JOIN chargelocation ON favorite.chargerDataSource = chargelocation.dataSource AND favorite.chargerId = chargelocation.id WHERE Within(chargelocation.coordinates, BuildMbr(:lng1, :lat1, :lng2, :lat2))")
    suspend fun getFavoritesInBoundsAsync(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double
    ): List<FavoriteWithDetail>

    @Query("SELECT * FROM favorite WHERE chargerDataSource == :dataSource AND chargerId == :chargerId")
    suspend fun findFavorite(chargerId: Long, dataSource: String): Favorite?
}