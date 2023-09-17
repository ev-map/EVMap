package net.vonforst.evmap.api

import android.content.Context
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import net.vonforst.evmap.R
import net.vonforst.evmap.api.goingelectric.GoingElectricApiWrapper
import net.vonforst.evmap.api.openchargemap.OpenChargeMapApiWrapper
import net.vonforst.evmap.api.openstreetmap.OpenStreetMapApiWrapper
import net.vonforst.evmap.model.*
import net.vonforst.evmap.viewmodel.Resource
import java.time.Duration
import java.time.Instant

interface ChargepointApi<out T : ReferenceData> {
    /**
     * Query for chargepoints within certain geographic bounds
     */
    suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<ChargepointList>

    /**
     * Query for chargepoints within a given radius in kilometers
     */
    suspend fun getChargepointsRadius(
        referenceData: ReferenceData,
        location: LatLng,
        radius: Int,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<ChargepointList>

    /**
     * Fetches detailed data for a specific charging site
     */
    suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation>

    suspend fun getReferenceData(): Resource<T>

    fun getFilters(referenceData: ReferenceData, sp: StringProvider): List<Filter<FilterValue>>

    fun convertFiltersToSQL(filters: FilterValues, referenceData: ReferenceData): FiltersSQLQuery

    fun filteringInSQLRequiresDetails(filters: FilterValues): Boolean

    val name: String
    val id: String

    /**
     * Duration we are limited to if there is a required API local cache time limit.
     */
    val cacheLimit: Duration

    /**
     * Whether this API supports querying for chargers at the backend
     *
     * This determines whether the getChargepoints, getChargepointsRadius and getChargepointDetail functions are supported.
     */
    val supportsOnlineQueries: Boolean

    /**
     * Whether this API supports downloading the whole dataset into local storage
     *
     * This determines whether the getAllChargepoints function is supported.
     */
    val supportsFullDownload: Boolean

    /**
     * Fetches all available chargers from this API.
     *
     * This may take a long time and should only be used when the user explicitly wants to download all chargers.
     *
     * TODO: add an optional callback parameter to this function to be able to receive updates on the download progress?
     * TODO: Should this also include getting the ReferenceData, instead of taking it as an argument?
     *       ReferenceData typically includes information that is needed to create the filter options, e.g.
     *       mappings between IDs and readable names (for operators etc.). So probably for OSM it makes sense
     *       to generate that within this function (e.g. build the list of available operators using all the
     *       operators found in the dataset).
     */
    suspend fun fullDownload(referenceData: ReferenceData): Sequence<ChargeLocation>
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

        "openstreetmap" -> {
            OpenStreetMapApiWrapper()
        }

        else -> throw IllegalArgumentException()
    }
}

data class FiltersSQLQuery(
    val query: String,
    val requiresChargepointQuery: Boolean,
    val requiresChargeCardQuery: Boolean
)

data class ChargepointList(val items: List<ChargepointListItem>, val isComplete: Boolean) {
    companion object {
        fun empty() = ChargepointList(emptyList(), true)
    }
}