package net.vonforst.evmap.api

import android.content.Context
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.model.*
import net.vonforst.evmap.viewmodel.Resource
import java.time.Duration

interface ChargepointApi<out T : ReferenceData> {
    suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<List<ChargepointListItem>>

    suspend fun getChargepointsRadius(
        referenceData: ReferenceData,
        location: LatLng,
        radius: Int,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<List<ChargepointListItem>>

    suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation>

    suspend fun getReferenceData(): Resource<T>

    fun getFilters(referenceData: ReferenceData, sp: StringProvider): List<Filter<FilterValue>>

    fun convertFiltersToSQL(filters: FilterValues): FiltersSQLQuery

    val name: String
    val id: String

    /**
     * Duration we are limited to if there is a required API local cache time limit.
     */
    val cacheLimit: Duration
}

interface StringProvider {
    fun getString(id: Int): String
}

fun Context.stringProvider() = object : StringProvider {
    override fun getString(id: Int): String {
        return this@stringProvider.getString(id)
    }
}

fun createApi(type: String, ctx: Context): ChargepointApi<ReferenceData> {
    return when (type) {
        "openchargemap" -> {
            OpenChargeMapApiWrapper(
                ctx.getString(
                    R.string.openchargemap_key
                )
            )
        }
        "goingelectric" -> {
            GoingElectricApiWrapper(
                ctx.getString(
                    R.string.goingelectric_key
                )
            )
        }
        else -> throw IllegalArgumentException()
    }
}

data class FiltersSQLQuery(
    val query: String,
    val requiresChargepointQuery: Boolean,
    val requiresChargeCardQuery: Boolean
)