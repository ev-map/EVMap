package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.model.FILTERS_CUSTOM

@Entity(
    indices = [Index(value = ["dataSource", "name"], unique = true)],
    primaryKeys = ["dataSource", "id"],
)
data class FilterProfile(
    val name: String,
    val dataSource: String,
    val id: Long,
    var order: Int = 0
) : Equatable

@Dao
interface FilterProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: FilterProfile): Long

    @Update
    suspend fun update(vararg profiles: FilterProfile)

    @Delete
    suspend fun delete(vararg profiles: FilterProfile)

    @Query("SELECT * FROM filterProfile WHERE dataSource = :dataSource AND id != $FILTERS_CUSTOM ORDER BY `order` ASC, `name` ASC")
    fun getProfiles(dataSource: String): LiveData<List<FilterProfile>>

    @Query("SELECT * FROM filterProfile WHERE dataSource = :dataSource AND name = :name")
    suspend fun getProfileByName(name: String, dataSource: String): FilterProfile?

    @Query("SELECT * FROM filterProfile WHERE dataSource = :dataSource AND id = :id")
    suspend fun getProfileById(id: Long, dataSource: String): FilterProfile?

    @Query("SELECT (MAX(id) + 1) FROM filterProfile WHERE dataSource = :dataSource")
    suspend fun getNewId(dataSource: String): Long
}