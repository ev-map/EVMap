package net.vonforst.evmap.storage

import androidx.lifecycle.*
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.car2go.maps.util.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.model.*
import net.vonforst.evmap.viewmodel.Resource
import net.vonforst.evmap.viewmodel.Status
import net.vonforst.evmap.viewmodel.await
import kotlin.math.sqrt

@Dao
abstract class ChargeLocationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg locations: ChargeLocation)

    // TODO: add max age here
    @Query("SELECT EXISTS(SELECT 1 FROM chargelocation WHERE dataSource == :dataSource AND id == :id AND isDetailed == 1)")
    abstract suspend fun checkExistsDetailed(id: Long, dataSource: String): Boolean

    suspend fun insertOrReplaceIfNoDetailedExists(vararg locations: ChargeLocation) {
        locations.forEach {
            if (it.isDetailed || !checkExistsDetailed(it.id, it.dataSource)) {
                insert(it)
            }
        }
    }

    @Delete
    abstract suspend fun delete(vararg locations: ChargeLocation)

    // TODO: add max age here
    @Query("SELECT * FROM chargelocation WHERE dataSource == :dataSource AND id == :id AND isDetailed == 1")
    abstract fun getChargeLocationById(id: Long, dataSource: String): LiveData<ChargeLocation>

    @SkipQueryVerification
    @Query("SELECT * FROM chargelocation WHERE dataSource == :dataSource AND Within(coordinates, BuildMbr(:lng1, :lat1, :lng2, :lat2))")
    abstract fun getChargeLocationsInBounds(
        lat1: Double,
        lat2: Double,
        lng1: Double,
        lng2: Double,
        dataSource: String
    ): LiveData<List<ChargeLocation>>

    @RawQuery(observedEntities = [ChargeLocation::class])
    abstract fun getChargeLocationsCustom(query: SupportSQLiteQuery): LiveData<List<ChargeLocation>>
}

/**
 * The ChargeLocationsRepository wraps the ChargepointApi and the DB to provide caching
 * functionality.
 */
class ChargeLocationsRepository(
    api: ChargepointApi<ReferenceData>, private val scope: CoroutineScope,
    private val db: AppDatabase, private val prefs: PreferenceDataSource
) {
    val api = MutableLiveData<ChargepointApi<ReferenceData>>().apply { value = api }

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

    private fun queryWithFilters(
        api: ChargepointApi<ReferenceData>,
        filters: FilterValues,
        bounds: LatLngBounds
    ) = try {
        val query = api.convertFiltersToSQL(filters)
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
            append(" AND Within(coordinates, BuildMbr(${bounds.southwest.longitude}, ${bounds.southwest.latitude}, ${bounds.northeast.longitude}, ${bounds.northeast.latitude})) ")
            append(query.query)
        }.toString()

        chargeLocationsDao.getChargeLocationsCustom(
            SimpleSQLiteQuery(
                sql,
                null
            )
        ) as LiveData<List<ChargepointListItem>>
    } catch (e: NotImplementedError) {
        MutableLiveData()  // in this case we cannot get a DB result
    }

    fun getChargepoints(
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues?
    ): LiveData<Resource<List<ChargepointListItem>>> {
        val api = api.value!!

        val dbResult = if (filters == null) {
            chargeLocationsDao.getChargeLocationsInBounds(
                bounds.southwest.latitude,
                bounds.northeast.latitude,
                bounds.southwest.longitude,
                bounds.northeast.longitude,
                prefs.dataSource
            ) as LiveData<List<ChargepointListItem>>
        } else {
            queryWithFilters(api, filters, bounds)
        }
        val apiResult = liveData {
            val refData = referenceData.await()
            val result = api.getChargepoints(refData, bounds, zoom, filters)

            emit(result)
            if (result.status == Status.SUCCESS) {
                chargeLocationsDao.insertOrReplaceIfNoDetailedExists(
                    *result.data!!.filterIsInstance(ChargeLocation::class.java)
                        .toTypedArray()
                )
            }
        }
        return CacheLiveData(dbResult, apiResult)
    }

    fun getChargepointsRadius(
        location: LatLng,
        radius: Int,
        zoom: Float,
        filters: FilterValues?
    ): LiveData<Resource<List<ChargepointListItem>>> {
        val api = api.value!!

        // database does not support radius queries, so let's build a square query instead
        val cornerDistance = radius * sqrt(2.0)
        val southwest = SphericalUtil.computeOffset(location, cornerDistance, 225.0)
        val northeast = SphericalUtil.computeOffset(location, cornerDistance, 45.0)
        val bounds = LatLngBounds(southwest, northeast)

        val dbResult = if (filters == null) {
            chargeLocationsDao.getChargeLocationsInBounds(
                bounds.southwest.latitude,
                bounds.northeast.latitude,
                bounds.southwest.longitude,
                bounds.northeast.longitude,
                prefs.dataSource,
            ) as LiveData<List<ChargepointListItem>>
        } else {
            queryWithFilters(api, filters, bounds)
        }
        val apiResult = liveData {
            val refData = referenceData.await()
            val result = api.getChargepointsRadius(refData, location, radius, zoom, filters)

            emit(result)
            if (result.status == Status.SUCCESS) {
                chargeLocationsDao.insertOrReplaceIfNoDetailedExists(
                    *result.data!!.filterIsInstance(ChargeLocation::class.java)
                        .toTypedArray()
                )
            }
        }
        return CacheLiveData(dbResult, apiResult)
    }

    fun getChargepointDetail(
        id: Long
    ): LiveData<Resource<ChargeLocation>> {
        val dbResult = chargeLocationsDao.getChargeLocationById(id, prefs.dataSource)
        val apiResult = liveData {
            emit(Resource.loading(null))
            val refData = referenceData.await()
            val result = api.value!!.getChargepointDetail(refData, id)
            emit(result)
            if (result.status == Status.SUCCESS) {
                chargeLocationsDao.insert(result.data!!)
            }
        }
        return CacheLiveData(dbResult, apiResult)
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
}