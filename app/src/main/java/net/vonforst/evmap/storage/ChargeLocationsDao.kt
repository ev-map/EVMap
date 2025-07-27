package net.vonforst.evmap.storage

import androidx.lifecycle.*
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import co.anbora.labs.spatia.geometry.Mbr
import co.anbora.labs.spatia.geometry.Polygon
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.car2go.maps.util.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.ChargepointList
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
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
import kotlin.math.sqrt

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
    abstract fun getChargeLocationById(
        id: Long,
        dataSource: String,
        after: Long
    ): LiveData<ChargeLocation>

    @SkipQueryVerification
    @Query("SELECT * FROM chargelocation WHERE dataSource == :dataSource AND Within(coordinates, BuildMbr(:lng1, :lat1, :lng2, :lat2)) AND timeRetrieved > :after")
    abstract fun getChargeLocationsInBounds(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double,
        dataSource: String,
        after: Long
    ): LiveData<List<ChargeLocation>>

    @SkipQueryVerification
    @Query("SELECT * FROM chargelocation WHERE dataSource == :dataSource AND PtDistWithin(coordinates, MakePoint(:lng, :lat, 4326), :radius) AND timeRetrieved > :after ORDER BY Distance(coordinates, MakePoint(:lng, :lat, 4326))")
    abstract fun getChargeLocationsRadius(
        lat: Double,
        lng: Double,
        radius: Double,
        dataSource: String,
        after: Long
    ): LiveData<List<ChargeLocation>>

    @RawQuery(observedEntities = [ChargeLocation::class])
    abstract fun getChargeLocationsCustom(query: SupportSQLiteQuery): LiveData<List<ChargeLocation>>

    @Query("SELECT COUNT(*) FROM chargelocation")
    abstract fun getCount(): LiveData<Long>

    @SkipQueryVerification
    @Query("SELECT SUM(pgsize) FROM dbstat WHERE name == \"ChargeLocation\"")
    abstract suspend fun getSize(): Long
}

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
            else -> {
                throw RuntimeException("no reference data implemented")
            }
        }
    }

    private val chargeLocationsDao = db.chargeLocationsDao()
    private val savedRegionDao = db.savedRegionDao()

    fun getChargepoints(
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues?,
        overrideCache: Boolean = false
    ): LiveData<Resource<ChargepointList>> {
        if (bounds.crossesAntimeridian()) {
            val (a, b) = bounds.splitAtAntimeridian()
            val liveDataA = getChargepoints(a, zoom, filters, overrideCache)
            val liveDataB = getChargepoints(b, zoom, filters, overrideCache)
            return combineLiveData(liveDataA, liveDataB)
        }
        
        
        val api = api.value!!

        val dbResult = if (filters == null) {
            chargeLocationsDao.getChargeLocationsInBounds(
                bounds.southwest.latitude,
                bounds.northeast.latitude,
                bounds.southwest.longitude,
                bounds.northeast.longitude,
                api.id,
                cacheLimitDate(api)
            ).map { Resource.success(it) }
        } else {
            queryWithFilters(api, filters, bounds)
        }.map {
            Resource(
                it.status,
                it.data?.let { ChargepointList(applyLocalClustering(it, zoom), true) },
                it.message
            )
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
    }

    private fun combineLiveData(
        liveDataA: LiveData<Resource<ChargepointList>>,
        liveDataB: LiveData<Resource<ChargepointList>>
    ) = MediatorLiveData<Resource<ChargepointList>>().apply {
        listOf(liveDataA, liveDataB).forEach {
            addSource(it) {
                val valA = liveDataA.value
                val valB = liveDataB.value
                val combinedList = if (valA?.data != null && valB?.data != null) {
                    ChargepointList(
                        valA.data.items + valB.data.items,
                        valA.data.isComplete && valB.data.isComplete
                    )
                } else if (valA?.data != null) {
                    ChargepointList(valA.data.items, false)
                } else if (valB?.data != null) {
                    ChargepointList(valB.data.items, false)
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
    ): LiveData<Resource<ChargepointList>> {
        val api = api.value!!

        val radiusMeters = radius.toDouble() * 1000
        val dbResult = if (filters == null) {
            chargeLocationsDao.getChargeLocationsRadius(
                location.latitude,
                location.longitude,
                radiusMeters,
                api.id,
                cacheLimitDate(api)
            ).map { Resource.success(it) }
        } else {
            queryWithFilters(api, filters, location, radiusMeters)
        }.map {
            Resource(
                it.status,
                it.data?.let { ChargepointList(applyLocalClustering(it, zoom), true) },
                it.message
            )
        }
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
        val apiResult = liveData {
            val refData = referenceData.await()
            val time = Instant.now()
            val result =
                api.getChargepointsRadius(refData, location, radius, zoom, useClustering, filters)
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
    }

    private fun applyLocalClustering(
        result: Resource<ChargepointList>,
        zoom: Float
    ): Resource<ChargepointList> {
        val list = result.data ?: return Resource(result.status, null, result.message)
        val chargers = list.items.filterIsInstance<ChargeLocation>()

        if (chargers.size != list.items.size) return Resource(
            result.status,
            list,
            result.message
        )  // list already contains clusters

        val clustered = applyLocalClustering(chargers, zoom)
        return Resource(result.status, ChargepointList(clustered, list.isComplete), result.message)
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
        val dbResult = chargeLocationsDao.getChargeLocationById(
            id,
            prefs.dataSource,
            cacheLimitDate(api.value!!)
        )
        val apiResult = liveData {
            emit(Resource.loading(null))
            val refData = referenceData.await()
            val result = api.value!!.getChargepointDetail(refData, id)
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
    ): LiveData<Resource<List<ChargeLocation>>> {
        val region =
            "Within(coordinates, BuildMbr(${bounds.southwest.longitude}, ${bounds.southwest.latitude}, ${bounds.northeast.longitude}, ${bounds.northeast.latitude}))"
        return queryWithFilters(api, filters, region)
    }

    private fun queryWithFilters(
        api: ChargepointApi<ReferenceData>,
        filters: FilterValues,
        location: LatLng,
        radius: Double
    ): LiveData<Resource<List<ChargeLocation>>> {
        val region =
            "PtDistWithin(coordinates, MakePoint(${location.longitude}, ${location.latitude}, 4326), ${radius})"
        val order =
            "ORDER BY Distance(coordinates, MakePoint(${location.longitude}, ${location.latitude}, 4326))"
        return queryWithFilters(api, filters, region, order)
    }

    private fun queryWithFilters(
        api: ChargepointApi<ReferenceData>,
        filters: FilterValues,
        regionSql: String,
        orderSql: String? = null
    ): LiveData<Resource<List<ChargeLocation>>> = referenceData.singleSwitchMap { refData ->
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

            chargeLocationsDao.getChargeLocationsCustom(
                SimpleSQLiteQuery(
                    sql,
                    null
                )
            ).map { Resource.success(it) }
        } catch (e: NotImplementedError) {
            MutableLiveData(
                Resource.error(
                    e.message,
                    null
                )
            )  // in this case we cannot get a DB result
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