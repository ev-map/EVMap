package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import net.vonforst.evmap.adapter.Equatable
import net.vonforst.evmap.model.FILTERS_CUSTOM

@Entity(
    indices = [Index(value = ["name"], unique = true)]
)
data class FilterProfile(
    val name: String,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

    @Query("SELECT * FROM filterProfile WHERE id != $FILTERS_CUSTOM ORDER BY `order` ASC, `name` ASC")
    fun getProfiles(): LiveData<List<FilterProfile>>

    @Query("SELECT * FROM filterProfile WHERE name = :name")
    suspend fun getProfileByName(name: String): FilterProfile?

    @Query("SELECT * FROM filterProfile WHERE id = :id")
    suspend fun getProfileById(id: Long): FilterProfile?
}