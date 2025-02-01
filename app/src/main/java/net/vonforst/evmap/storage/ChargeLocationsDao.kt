package net.vonforst.evmap.storage

import android.util.Log
import androidx.lifecycle.*
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import co.anbora.labs.spatia.geometry.Mbr
import co.anbora.labs.spatia.geometry.MultiPolygon
import co.anbora.labs.spatia.geometry.Point
import co.anbora.labs.spatia.geometry.Polygon
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.ChargepointList
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.api.openstreetmap.OSMReferenceData
import net.vonforst.evmap.api.openstreetmap.OpenStreetMapApiWrapper
import net.vonforst.evmap.model.*
import net.vonforst.evmap.ui.cluster
import net.vonforst.evmap.utils.crossesAntimeridian
import net.vonforst.evmap.utils.splitAtAntimeridian
import net.vonforst.evmap.viewmodel.Resource
import net.vonforst.evmap.viewmodel.Status
import net.vonforst.evmap.viewmodel.await
import net.vonforst.evmap.viewmodel.getClusterDistance
import net.vonforst.evmap.viewmodel.singleSwitchMap
import java.time.Duration
import java.time.Instant
import kotlin.math.pow
import kotlin.math.roundToInt

@Dao
abstract class ChargeLocationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg locations: ChargeLocation)

    @Query("SELECT EXISTS(SELECT 1 FROM chargelocation WHERE dataSource == :dataSource AND id == :id AND isDetailed == 1 AND timeRetrieved > :after )")
    abstract suspend fun checkExistsDetailed(id: Long, dataSource: String, after: Long): Boolean

    suspend fun insertOrReplaceIfNoDetailedExists(
        afterDate: Long,
        vararg locations: ChargeLocation
    ) {
        locations.forEach {
            if (it.isDetailed || !checkExistsDetailed(it.id, it.dataSource, afterDate)) {
                insert(it)
            }
        }
    }

    @Delete
    abstract suspend fun delete(vararg locations: ChargeLocation)

    @Query("DELETE FROM chargelocation WHERE dataSource == :dataSource AND timeRetrieved <= :before AND NOT EXISTS (SELECT 1 FROM favorite WHERE favorite.chargerId = chargelocation.id)")
    abstract suspend fun deleteOutdatedIfNotFavorite(dataSource: String, before: Long)

    @Query("DELETE FROM chargelocation WHERE NOT EXISTS (SELECT 1 FROM favorite WHERE favorite.chargerId = chargelocation.id)")
    abstract suspend fun deleteAllIfNotFavorite()

    @Query("SELECT * FROM chargelocation WHERE dataSource == :dataSource AND id == :id AND isDetailed == 1 AND timeRetrieved > :after")
    abstract suspend fun getChargeLocationById(
        id: Long,
        dataSource: String,
        after: Long
    ): ChargeLocation?

    @Query("SELECT * FROM chargelocation WHERE dataSource == :dataSource AND id IN (:ids) AND timeRetrieved > :after")
    abstract suspend fun getChargeLocationsById(
        ids: List<Long>,
        dataSource: String,
        after: Long
    ): List<ChargeLocation>

    @SkipQueryVerification
    @Query("SELECT * FROM chargelocation WHERE dataSource == :dataSource AND timeRetrieved > :after AND ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = 'ChargeLocation' AND search_frame = BuildMbr(:lng1, :lat1, :lng2, :lat2))")
    abstract suspend fun getChargeLocationsInBounds(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double,
        dataSource: String,
        after: Long
    ): List<ChargeLocation>

    @SkipQueryVerification
    @Query("SELECT * FROM chargelocation WHERE dataSource == :dataSource AND PtDistWithin(coordinates, MakePoint(:lng, :lat, 4326), :radius) AND timeRetrieved > :after AND ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = 'ChargeLocation' AND search_frame = BuildCircleMbr(:lng, :lat, :radius)) ORDER BY Distance(coordinates, MakePoint(:lng, :lat, 4326))")
    abstract suspend fun getChargeLocationsRadius(
        lat: Double,
        lng: Double,
        radius: Double,
        dataSource: String,
        after: Long
    ): List<ChargeLocation>

    @RawQuery(observedEntities = [ChargeLocation::class])
    abstract suspend fun getChargeLocationsCustom(query: SupportSQLiteQuery): List<ChargeLocation>

    @SkipQueryVerification
    @Query("SELECT SUM(1) AS clusterCount, MakePoint(AVG(X(coordinates)), AVG(Y(coordinates)), 4326) as center, SnapToGrid(coordinatesProjected, :precision) AS snapped, GROUP_CONCAT(id, ',') as ids FROM chargelocation WHERE dataSource == :dataSource AND timeRetrieved > :after AND ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = 'ChargeLocation' AND f_geometry_column = 'coordinates' AND search_frame = BuildMbr(:lng1, :lat1, :lng2, :lat2)) GROUP BY snapped")
    abstract suspend fun getChargeLocationClusters(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double,
        dataSource: String,
        after: Long,
        precision: Double
    ): List<DBChargeLocationCluster>

    suspend fun getChargeLocationsClustered(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double,
        dataSource: String,
        after: Long,
        zoom: Float
    ): List<ChargepointListItem> {
        val precision = 30000000 / 2.0.pow(zoom.roundToInt() + 1)
        val clusters =
            getChargeLocationClusters(lat1, lat2, lng1, lng2, dataSource, after, precision)
        val singleChargers =
            getChargeLocationsById(clusters.filter { it.clusterCount == 1 }.map { it.ids }
                .flatten(), dataSource, after)
        return clusters.filter { it.clusterCount > 1 }.map { it.convert() } + singleChargers
    }

    @Query("SELECT COUNT(*) FROM chargelocation")
    abstract fun getCount(): LiveData<Long>

    @Query("SELECT COUNT(*) FROM chargelocation")
    abstract suspend fun getCountAsync(): Long

    @SkipQueryVerification
    @Query("SELECT SUM(pgsize) FROM dbstat WHERE name == \"ChargeLocation\"")
    abstract suspend fun getSize(): Long
}

data class DBChargeLocationCluster(
    @ColumnInfo("clusterCount") val clusterCount: Int,
    @ColumnInfo("center") val center: Coordinate,
    @ColumnInfo("snapped") val snapped: Point,
    @ColumnInfo("ids") val ids: List<Long>
) {
    fun convert() = ChargeLocationCluster(clusterCount, center, null)
}

private const val TAG = "ChargeLocationsDao"

/**
 * The ChargeLocationsRepository wraps the ChargepointApi and the DB to provide caching
 * and clustering functionality.
 */
class ChargeLocationsRepository(
    api: ChargepointApi<ReferenceData>, private val scope: CoroutineScope,
    private val db: AppDatabase, private val prefs: PreferenceDataSource
) {
    val api = MutableLiveData<ChargepointApi<ReferenceData>>().apply { value = api }

    // if zoom level is below this value, server-side clustering will be used (if the API provides it)
    private val serverSideClusteringThreshold = 9f
    private fun shouldUseServerSideClustering(zoom: Float) = zoom < serverSideClusteringThreshold

    // if cached data is available and more recent than this duration, API will not be queried
    private val cacheSoftLimit = Duration.ofDays(1)

    val referenceData = this.api.switchMap { api ->
        when (api) {
            is GoingElectricApiWrapper -> {
                GEReferenceDataRepository(
                    api,
                    scope,
                    db.geReferenceDataDao(),
                    prefs
                ).getReferenceData()
            }

            is OpenChargeMapApiWrapper -> {
                OCMReferenceDataRepository(
                    api,
                    scope,
                    db.ocmReferenceDataDao(),
                    prefs
                ).getReferenceData()
            }

            is OpenStreetMapApiWrapper -> {
                OSMReferenceDataRepository(db.osmReferenceDataDao()).getReferenceData()
            }

            else -> {
                throw RuntimeException("no reference data implemented")
            }
        }
    }

    private val chargeLocationsDao = db.chargeLocationsDao()
    private val savedRegionDao = db.savedRegionDao()
    private var fullDownloadJob: Job? = null
    private var fullDownloadProgress: MutableStateFlow<Float?> = MutableStateFlow(null)

    fun getChargepoints(
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues?,
        overrideCache: Boolean = false
    ): LiveData<Resource<List<ChargepointListItem>>> {
        if (bounds.crossesAntimeridian()) {
            val (a, b) = bounds.splitAtAntimeridian()
            val liveDataA = getChargepoints(a, zoom, filters, overrideCache)
            val liveDataB = getChargepoints(b, zoom, filters, overrideCache)
            return combineLiveData(liveDataA, liveDataB)
        }

        val api = api.value!!
        val t1 = System.currentTimeMillis()
        val filters: FilterValues? = null
        val dbResult = if (filters.isNullOrEmpty()) {
            liveData {
                emit(
                    chargeLocationsDao.getChargeLocationsClustered(
                        bounds.southwest.latitude,
                        bounds.northeast.latitude,
                        bounds.southwest.longitude,
                        bounds.northeast.longitude,
                        api.id,
                        cacheLimitDate(api),
                        zoom
                    )
                )
            }
        } else {
            queryWithFilters(api, filters, bounds).map {
                applyLocalClustering(it, zoom) // TODO: use DB clustering
            }
        }.map {
            val t2 = System.currentTimeMillis()
            Log.d(TAG, "DB loading time: ${t2 - t1}")
            Log.d(TAG, "number of chargers: ${it.size}")
            it
        }
        val filtersSerialized =
            filters?.filter { it.value != it.filter.defaultValue() }?.takeIf { it.isNotEmpty() }
                ?.serialize()
        val requiresDetail = filters?.let { api.filteringInSQLRequiresDetails(it) } ?: false
        val savedRegionResult = savedRegionDao.savedRegionCovers(
            bounds.southwest.latitude,
            bounds.northeast.latitude,
            bounds.southwest.longitude,
            bounds.northeast.longitude,
            api.id,
            cacheSoftLimitDate(api),
            filtersSerialized,
            requiresDetail
        )
        val useClustering = shouldUseServerSideClustering(zoom)
        if (api.supportsOnlineQueries) {
            val apiResult = liveData {
                val refData = referenceData.await()
                val time = Instant.now()
                val result = api.getChargepoints(refData, bounds, zoom, useClustering, filters)
                emit(applyLocalClustering(result, zoom))
                if (result.status == Status.SUCCESS) {
                    val chargers = result.data!!.items.filterIsInstance<ChargeLocation>()
                    chargeLocationsDao.insertOrReplaceIfNoDetailedExists(
                        cacheLimitDate(api), *chargers.toTypedArray()
                    )
                    if (chargers.size == result.data.items.size && result.data.isComplete) {
                        val region = Mbr(
                            bounds.southwest.longitude,
                            bounds.southwest.latitude,
                            bounds.northeast.longitude,
                            bounds.northeast.latitude, 4326
                        ).asPolygon()
                        savedRegionDao.insert(
                            SavedRegion(
                                region, api.id, time,
                                filtersSerialized,
                                false
                            )
                        )
                    }
                }
            }
            return if (overrideCache) {
                apiResult
            } else {
                CacheLiveData(dbResult, apiResult, savedRegionResult).distinctUntilChanged()
            }
        } else {
            return liveData {
                if (fullDownloadJob != null) {
                    fullDownloadProgress.value?.let { emit(Resource.loading(null, it)) }
                }
                if (!savedRegionResult.await()) {
                    val job = fullDownloadJob ?: scope.launch {
                        fullDownload()
                    }.also { fullDownloadJob = it }
                    val progressJob = scope.launch {
                        fullDownloadProgress.collect {
                            emit(Resource.loading(null, it))
                        }
                    }
                    job.join()
                    progressJob.cancelAndJoin()
                }
                emit(Resource.success(dbResult.await()))
            }
        }
    }

    private fun combineLiveData(
        liveDataA: LiveData<Resource<List<ChargepointListItem>>>,
        liveDataB: LiveData<Resource<List<ChargepointListItem>>>
    ) = MediatorLiveData<Resource<List<ChargepointListItem>>>().apply {
        listOf(liveDataA, liveDataB).forEach {
            addSource(it) {
                val valA = liveDataA.value
                val valB = liveDataB.value
                val combinedList = if (valA?.data != null && valB?.data != null) {
                    valA.data + valB.data
                } else if (valA?.data != null) {
                    valA.data
                } else if (valB?.data != null) {
                    valB.data
                } else null
                if (valA?.status == Status.SUCCESS && valB?.status == Status.SUCCESS) {
                    Resource.success(combinedList)
                } else if (valA?.status == Status.ERROR || valB?.status == Status.ERROR) {
                    Resource.error(valA?.message ?: valB?.message, combinedList)
                } else {
                    Resource.loading(combinedList)
                }
            }
        }
    }

    fun getChargepointsRadius(
        location: LatLng,
        radius: Int,
        zoom: Float,
        filters: FilterValues?
    ): LiveData<Resource<List<ChargepointListItem>>> {
        val api = api.value!!

        val radiusMeters = radius.toDouble() * 1000
        val dbResult = if (filters.isNullOrEmpty()) {
            liveData {
                emit(
                    chargeLocationsDao.getChargeLocationsRadius(
                        location.latitude,
                        location.longitude,
                        radiusMeters,
                        api.id,
                        cacheLimitDate(api)
                    )
                )
            }
        } else {
            queryWithFilters(api, filters, location, radiusMeters)
        }.map { applyLocalClustering(it, zoom) }
        val filtersSerialized =
            filters?.filter { it.value != it.filter.defaultValue() }?.takeIf { it.isNotEmpty() }
                ?.serialize()
        val requiresDetail = filters?.let { api.filteringInSQLRequiresDetails(it) } ?: false
        val savedRegionResult = savedRegionDao.savedRegionCoversRadius(
            location.latitude,
            location.longitude,
            radiusMeters * 0.999,  // to account for float rounding errors
            api.id,
            cacheSoftLimitDate(api),
            filtersSerialized,
            requiresDetail
        )
        val useClustering = shouldUseServerSideClustering(zoom)
        if (api.supportsOnlineQueries) {
            val apiResult = liveData {
                val refData = referenceData.await()
                val time = Instant.now()
                val result =
                    api.getChargepointsRadius(
                        refData,
                        location,
                        radius,
                        zoom,
                        useClustering,
                        filters
                    )
                emit(applyLocalClustering(result, zoom))
                if (result.status == Status.SUCCESS) {
                    val chargers = result.data!!.items.filterIsInstance<ChargeLocation>()
                    chargeLocationsDao.insertOrReplaceIfNoDetailedExists(
                        cacheLimitDate(api), *chargers.toTypedArray()
                    )
                    if (chargers.size == result.data.items.size && result.data.isComplete) {
                        val region = Polygon(
                            savedRegionDao.makeCircle(
                                location.latitude,
                                location.longitude,
                                radiusMeters
                            )
                        )
                        savedRegionDao.insert(
                            SavedRegion(
                                region, api.id, time,
                                filtersSerialized,
                                false
                            )
                        )
                    }
                }
            }
            return CacheLiveData(dbResult, apiResult, savedRegionResult).distinctUntilChanged()
        } else {
            return liveData {
                if (!savedRegionResult.await()) {
                    val job = fullDownloadJob ?: scope.launch {
                        fullDownload()
                    }.also { fullDownloadJob = it }
                    val progressJob = scope.launch {
                        fullDownloadProgress.collect {
                            emit(Resource.loading(null, it))
                        }
                    }
                    job.join()
                    progressJob.cancelAndJoin()
                }
                emit(Resource.success(dbResult.await()))
            }
        }
    }

    private fun applyLocalClustering(
        result: Resource<ChargepointList>,
        zoom: Float
    ): Resource<List<ChargepointListItem>> {
        val list = result.data ?: return Resource(result.status, null, result.message)
        val chargers = list.items.filterIsInstance<ChargeLocation>()

        if (chargers.size != list.items.size) return Resource(
            result.status,
            list.items,
            result.message
        )  // list already contains clusters

        val clustered = applyLocalClustering(chargers, zoom)
        return Resource(result.status, clustered, result.message)
    }

    private fun applyLocalClustering(
        chargers: List<ChargeLocation>,
        zoom: Float
    ): List<ChargepointListItem> {
        /* in very crowded places (good example: central London on OpenChargeMap without filters)
           we have to cluster even at pretty high zoom levels to make sure the map does not get
           laggy. Otherwise, only cluster at zoom levels <= 11. */
        val useClustering = chargers.size > 500 || zoom <= 11f
        val clusterDistance = getClusterDistance(zoom)

        val chargersClustered = if (useClustering && clusterDistance != null) {
            Dispatchers.Default.run {
                cluster(chargers, zoom, clusterDistance)
            }
        } else chargers
        return chargersClustered
    }

    fun getChargepointDetail(
        id: Long,
        overrideCache: Boolean = false
    ): LiveData<Resource<ChargeLocation>> {
        val api = api.value!!
        val dbResult = liveData {
            emit(
                chargeLocationsDao.getChargeLocationById(
                    id,
                    prefs.dataSource,
                    cacheLimitDate(api)
                )
            )
        }
        if (api.supportsOnlineQueries) {
            val apiResult = liveData {
                emit(Resource.loading(null))
                val refData = referenceData.await()
                val result = api.getChargepointDetail(refData, id)
                emit(result)
                if (result.status == Status.SUCCESS) {
                    chargeLocationsDao.insert(result.data!!)
                }
            }
            return if (overrideCache) {
                apiResult
            } else {
                PreferCacheLiveData(dbResult, apiResult, cacheSoftLimit)
            }
        } else {
            return dbResult.map { Resource.success(it) }
        }
    }

    fun getFilters(sp: StringProvider) = MediatorLiveData<List<Filter<FilterValue>>>().apply {
        addSource(referenceData) { refData: ReferenceData? ->
            refData?.let { value = api.value!!.getFilters(refData, sp) }
        }
    }

    suspend fun getFiltersAsync(sp: StringProvider): List<Filter<FilterValue>> {
        val refData = referenceData.await()
        return api.value!!.getFilters(refData, sp)
    }

    val chargeCardMap by lazy {
        referenceData.map { refData: ReferenceData? ->
            if (refData is GEReferenceData) {
                refData.chargecards.associate {
                    it.id to it.convert()
                }
            } else {
                null
            }
        }
    }

    private fun queryWithFilters(
        api: ChargepointApi<ReferenceData>,
        filters: FilterValues,
        bounds: LatLngBounds
    ): LiveData<List<ChargeLocation>> {
        val region =
            "ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = 'ChargeLocation' AND search_frame = BuildMbr(${bounds.southwest.longitude}, ${bounds.southwest.latitude}, ${bounds.northeast.longitude}, ${bounds.northeast.latitude}))"
        return queryWithFilters(api, filters, region)
    }

    private fun queryWithFilters(
        api: ChargepointApi<ReferenceData>,
        filters: FilterValues,
        location: LatLng,
        radius: Double
    ): LiveData<List<ChargeLocation>> {
        val region =
            "PtDistWithin(coordinates, MakePoint(${location.longitude}, ${location.latitude}, 4326), ${radius}) AND ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = 'ChargeLocation' AND search_frame = BuildCircleMbr(${location.longitude}, ${location.latitude}, $radius))"
        val order =
            "ORDER BY Distance(coordinates, MakePoint(${location.longitude}, ${location.latitude}, 4326))"
        return queryWithFilters(api, filters, region, order)
    }

    private fun queryWithFilters(
        api: ChargepointApi<ReferenceData>,
        filters: FilterValues,
        regionSql: String,
        orderSql: String? = null
    ): LiveData<List<ChargeLocation>> = referenceData.singleSwitchMap { refData ->
        try {
            val query = api.convertFiltersToSQL(filters, refData)
            val after = cacheLimitDate(api)
            val sql = StringBuilder().apply {
                append("SELECT")
                if (query.requiresChargeCardQuery or query.requiresChargepointQuery) {
                    append(" DISTINCT chargelocation.*")
                } else {
                    append(" *")
                }
                append(" FROM chargelocation")
                if (query.requiresChargepointQuery) {
                    append(" JOIN json_each(chargelocation.chargepoints) AS cp")
                }
                if (query.requiresChargeCardQuery) {
                    append(" JOIN json_each(chargelocation.chargecards) AS cc")
                }
                append(" WHERE dataSource == '${prefs.dataSource}'")
                append(" AND $regionSql")
                append(" AND timeRetrieved > $after")
                append(query.query)
                orderSql?.let { append(" " + orderSql) }
            }.toString()

            liveData {
                emit(
                    chargeLocationsDao.getChargeLocationsCustom(
                        SimpleSQLiteQuery(
                            sql,
                            null
                        )
                    )
                )
            }
        } catch (e: NotImplementedError) {
            MutableLiveData()  // in this case we cannot get a DB result
        }
    }

    private suspend fun fullDownload() {
        val api = api.value!!
        if (!api.supportsFullDownload) return

        val time = Instant.now()
        val result = api.fullDownload()
        try {
            var insertJob: Job? = null
            result.chargers.chunked(1024).forEach {
                insertJob?.join()
                insertJob = withContext(Dispatchers.IO) {
                    scope.launch {
                        chargeLocationsDao.insert(*it.toTypedArray())
                    }
                }
                fullDownloadProgress.value = result.progress
            }
            val region = Mbr(
                -180.0,
                -90.0,
                180.0,
                90.0, 4326
            ).asPolygon()
            savedRegionDao.insert(
                SavedRegion(
                    region, api.id, time,
                    null,
                    true
                )
            )

            when (api) {
                is OpenStreetMapApiWrapper -> {
                    val refData = result.referenceData
                    OSMReferenceDataRepository(db.osmReferenceDataDao()).updateReferenceData(refData as OSMReferenceData)
                }
            }

        } finally {
            fullDownloadProgress.value = null
        }
    }


    private fun cacheLimitDate(api: ChargepointApi<ReferenceData>): Long {
        val cacheLimit = api.cacheLimit
        return Instant.now().minus(cacheLimit).toEpochMilli()
    }

    private fun cacheSoftLimitDate(api: ChargepointApi<ReferenceData>): Long {
        val cacheLimit = maxOf(api.cacheLimit, Duration.ofDays(2))
        return Instant.now().minus(cacheLimit).toEpochMilli()
    }
}