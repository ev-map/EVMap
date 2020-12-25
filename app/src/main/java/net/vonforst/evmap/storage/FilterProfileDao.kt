package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import net.vonforst.evmap.viewmodel.FILTERS_CUSTOM

@Entity(
    indices = [Index(value = ["name"], unique = true)]
)
data class FilterProfile(val name: String, @PrimaryKey(autoGenerate = true) val id: Long = 0)

@Dao
interface FilterProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: FilterProfile): Long

    @Delete
    suspend fun delete(vararg profiles: FilterProfile)

    @Query("SELECT * FROM filterProfile WHERE id != $FILTERS_CUSTOM")
    fun getProfiles(): LiveData<List<FilterProfile>>

    @Query("SELECT * FROM filterProfile WHERE name = :name")
    suspend fun getProfileByName(name: String): FilterProfile?
}