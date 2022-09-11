package net.vonforst.evmap.storage

import androidx.lifecycle.*
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import kotlinx.coroutines.CoroutineScope
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.goingelectric.GEReferenceData
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.model.*
import net.vonforst.evmap.viewmodel.Resource
import net.vonforst.evmap.viewmodel.await

@Dao
abstract class ChargeLocationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg locations: ChargeLocation)

    @Delete
    abstract suspend fun delete(vararg locations: ChargeLocation)
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

    fun getChargepoints(
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues?
    ): LiveData<Resource<List<ChargepointListItem>>> {
        return liveData {
            val refData = referenceData.await()
            val result = api.value!!.getChargepoints(refData, bounds, zoom, filters)

            emit(result)
        }
    }

    fun getChargepointsRadius(
        location: LatLng,
        radius: Int,
        zoom: Float,
        filters: FilterValues?
    ): LiveData<Resource<List<ChargepointListItem>>> {
        return liveData {
            val refData = referenceData.await()
            val result = api.value!!.getChargepointsRadius(refData, location, radius, zoom, filters)

            emit(result)
        }
    }

    fun getChargepointDetail(
        id: Long
    ): LiveData<Resource<ChargeLocation>> {
        return liveData {
            val refData = referenceData.await()
            val result = api.value!!.getChargepointDetail(refData, id)
            emit(result)
        }
    }

    fun getFilters(sp: StringProvider) = MediatorLiveData<List<Filter<FilterValue>>>().apply {
        addSource(referenceData) { refData: ReferenceData? ->
            refData?.let { value = api.value!!.getFilters(refData, sp) }
        }
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