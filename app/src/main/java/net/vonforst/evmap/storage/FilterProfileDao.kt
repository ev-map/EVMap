package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.room.*
import net.vonforst.evmap.viewmodel.BooleanFilterValue
import net.vonforst.evmap.viewmodel.FilterValue
import net.vonforst.evmap.viewmodel.MultipleChoiceFilterValue
import net.vonforst.evmap.viewmodel.SliderFilterValue

@Entity
data class FilterProfile(@PrimaryKey val id: Long, val name: String)

data class FilterProfileDetail(
    @Embedded val filterProfile: FilterProfile,
    @Relation(
        parentColumn = "id",
        entityColumn = "profile"
    )
    val sliderValues: List<SliderFilterValue>,
    @Relation(
        parentColumn = "id",
        entityColumn = "profile"
    )
    val booleanValues: List<BooleanFilterValue>,
    @Relation(
        parentColumn = "id",
        entityColumn = "profile"
    )
    val multipleChoiceValues: List<MultipleChoiceFilterValue>
) {
    val values: List<FilterValue>
        get() = sliderValues + booleanValues + multipleChoiceValues
}

@Dao
interface FilterProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg profiles: FilterProfile)

    @Delete
    suspend fun delete(vararg profiles: FilterProfile)

    @Query("SELECT * FROM filterProfile")
    fun getProfiles(): LiveData<List<FilterProfile>>

    @Query("SELECT * FROM filterProfile")
    fun getProfileDetails(): LiveData<List<FilterProfileDetail>>
}