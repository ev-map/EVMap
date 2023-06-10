package net.vonforst.evmap.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.*
import co.anbora.labs.spatia.geometry.Geometry
import co.anbora.labs.spatia.geometry.LineString
import co.anbora.labs.spatia.geometry.Polygon
import net.vonforst.evmap.utils.circleAsEllipse
import java.time.Instant

@Entity(
    indices = [Index(value = ["filters", "dataSource"])]
)
data class SavedRegion(
    val region: Polygon,
    val dataSource: String,
    val timeRetrieved: Instant,
    val filters: String?,
    val isDetailed: Boolean,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null
)

@Dao
abstract class SavedRegionDao {
    @SkipQueryVerification
    @Query("SELECT GUnion(region) FROM savedregion WHERE dataSource == :dataSource AND timeRetrieved > :after AND (filters == :filters OR filters IS NULL) AND (isDetailed OR NOT :isDetailed)")
    abstract fun getSavedRegion(
        dataSource: String,
        after: Long,
        filters: String? = null,
        isDetailed: Boolean = false
    ): Geometry

    @SkipQueryVerification
    @Query("SELECT Covers(GUnion(region), BuildMbr(:lng1, :lat1, :lng2, :lat2, 4326)) FROM savedregion WHERE dataSource == :dataSource AND timeRetrieved > :after AND Intersects(region, BuildMbr(:lng1, :lat1, :lng2, :lat2, 4326)) AND (filters == :filters OR filters IS NULL) AND (isDetailed OR NOT :isDetailed)")
    protected abstract fun savedRegionCoversInt(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double,
        dataSource: String, after: Long, filters: String? = null, isDetailed: Boolean = false
    ): LiveData<Int>

    @SkipQueryVerification
    @Query("SELECT Covers(GUnion(region), MakeEllipse(:lng, :lat, :radiusLng, :radiusLat, 4326)) FROM savedregion WHERE dataSource == :dataSource AND timeRetrieved > :after AND Intersects(region, MakeEllipse(:lng, :lat, :radiusLng, :radiusLat, 4326)) AND (filters == :filters OR filters IS NULL) AND (isDetailed OR NOT :isDetailed)")
    protected abstract fun savedRegionCoversRadiusInt(
        lat: Double,
        lng: Double,
        radiusLat: Double,
        radiusLng: Double,
        dataSource: String, after: Long, filters: String? = null, isDetailed: Boolean = false
    ): LiveData<Int>

    fun savedRegionCovers(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double,
        dataSource: String, after: Long, filters: String? = null, isDetailed: Boolean = false
    ): LiveData<Boolean> {
        return savedRegionCoversInt(
            lat1,
            lat2,
            lng1,
            lng2,
            dataSource,
            after,
            filters,
            isDetailed
        ).map { it == 1 }
    }

    fun savedRegionCoversRadius(
        lat: Double,
        lng: Double,
        radius: Double,
        dataSource: String, after: Long, filters: String? = null, isDetailed: Boolean = false
    ): LiveData<Boolean> {
        val (radiusLat, radiusLng) = circleAsEllipse(lat, lng, radius)
        return savedRegionCoversRadiusInt(
            lat,
            lng,
            radiusLat,
            radiusLng,
            dataSource,
            after,
            filters,
            isDetailed
        ).map { it == 1 }
    }

    @Insert
    abstract suspend fun insert(savedRegion: SavedRegion)

    @Query("DELETE FROM savedregion WHERE dataSource == :dataSource AND timeRetrieved <= :before")
    abstract suspend fun deleteOutdated(dataSource: String, before: Long)

    @SkipQueryVerification
    @Query("SELECT MakeEllipse(:lng, :lat, :radiusLng, :radiusLat, 4326)")
    protected abstract suspend fun makeEllipse(
        lat: Double, lng: Double,
        radiusLat: Double, radiusLng: Double
    ): LineString

    suspend fun makeCircle(lat: Double, lng: Double, radius: Double): LineString {
        val (radiusLat, radiusLng) = circleAsEllipse(lat, lng, radius)
        return makeEllipse(lat, lng, radiusLat, radiusLng)
    }
}