package net.vonforst.evmap.api.nobil

import android.content.Context
import android.database.DatabaseUtils
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.addDebugInterceptors
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.ChargepointList
import net.vonforst.evmap.api.FiltersSQLQuery
import net.vonforst.evmap.api.FullDownloadResult
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.mapPower
import net.vonforst.evmap.api.mapPowerInverse
import net.vonforst.evmap.api.powerSteps
import net.vonforst.evmap.model.BooleanFilter
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.ChargepointListItem
import net.vonforst.evmap.model.Filter
import net.vonforst.evmap.model.FilterValue
import net.vonforst.evmap.model.FilterValues
import net.vonforst.evmap.model.MultipleChoiceFilter
import net.vonforst.evmap.model.ReferenceData
import net.vonforst.evmap.model.SliderFilter
import net.vonforst.evmap.model.getBooleanValue
import net.vonforst.evmap.model.getMultipleChoiceValue
import net.vonforst.evmap.model.getSliderValue
import net.vonforst.evmap.viewmodel.Resource
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.time.Duration

private const val maxResults = 2000

interface NobilApi {
    @POST("search.php")
    suspend fun getChargepoints(
        @Body request: NobilRectangleSearchRequest
    ): Response<NobilResponseData>

    @POST("search.php")
    suspend fun getChargepointsRadius(
        @Body request: NobilRadiusSearchRequest
    ): Response<NobilResponseData>

    @POST("search.php")
    suspend fun getChargepointDetail(
        @Body request: NobilDetailSearchRequest
    ): Response<NobilResponseData>

    companion object {
        private val cacheSize = 10L * 1024 * 1024 // 10MB

        private val moshi = Moshi.Builder()
            .add(LocalDateTimeAdapter())
            .add(CoordinateAdapter())
            .build()

        fun create(
            baseurl: String,
            context: Context?
        ): NobilApi {
            val client = OkHttpClient.Builder().apply {
                if (BuildConfig.DEBUG) {
                    addDebugInterceptors()
                }
                if (context != null) {
                    cache(Cache(context.cacheDir, cacheSize))
                }
            }.build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseurl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
            return retrofit.create(NobilApi::class.java)
        }
    }
}

class NobilApiWrapper(
    val apikey: String,
    baseurl: String = "https://nobil.no/api/server/",
    context: Context? = null
) : ChargepointApi<NobilReferenceData> {
    override val name = "Nobil"
    override val id = "nobil"
    override val supportsOnlineQueries = true
    override val supportsFullDownload = false
    override val cacheLimit = Duration.ofDays(300L)
    val api = NobilApi.create(baseurl, context)

    override suspend fun fullDownload(): FullDownloadResult<NobilReferenceData> {
        throw NotImplementedError()
    }

    override suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?,
    ): Resource<ChargepointList> {
        try {
            val northeast = "(" + bounds.northeast.latitude + ", " + bounds.northeast.longitude + ")"
            val southwest = "(" + bounds.southwest.latitude + ", " + bounds.southwest.longitude + ")"
            val request = NobilRectangleSearchRequest(apikey, northeast, southwest, maxResults)
            val response = api.getChargepoints(request)
            if (!response.isSuccessful) {
                return Resource.error(response.message(), null)
            }

            val data = response.body()!!
            if (data.chargerStations == null) {
                return Resource.success(ChargepointList.empty())
            }
            val result = postprocessResult(
                data,
                filters
            )
            return Resource.success(ChargepointList(result, data.chargerStations.size < maxResults))
        } catch (e: IOException) {
            return Resource.error(e.message, null)
        } catch (e: HttpException) {
            return Resource.error(e.message, null)
        }
    }

    override suspend fun getChargepointsRadius(
        referenceData: ReferenceData,
        location: LatLng,
        radius: Int,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<ChargepointList> {
        try {
            val request = NobilRadiusSearchRequest(apikey, location.latitude, location.longitude, radius * 1000.0, maxResults)
            val response = api.getChargepointsRadius(request)
            if (!response.isSuccessful) {
                return Resource.error(response.message(), null)
            }

            val data = response.body()!!
            if (data.chargerStations == null) {
                return Resource.error(response.message(), null)
            }
            val result = postprocessResult(
                data,
                filters
            )
            return Resource.success(ChargepointList(result, data.chargerStations.size < maxResults))
        } catch (e: IOException) {
            return Resource.error(e.message, null)
        } catch (e: HttpException) {
            return Resource.error(e.message, null)
        }
    }

    private fun postprocessResult(
        data: NobilResponseData,
        filters: FilterValues?
    ): List<ChargepointListItem> {
        if (data.rights == null ) throw JsonDataException("Rights field is missing in received data")

        return data.chargerStations!!.mapNotNull { it.convert(data.rights, filters) }.distinct()
    }

    override suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation> {
        // TODO: Nobil ids are "SWE_1234", not Long
        return Resource.error("getChargepointDetail is not implemented", null)
    }

    override suspend fun getReferenceData(): Resource<NobilReferenceData> {
        return Resource.success(NobilReferenceData(0))
    }

    override fun getFilters(
        referenceData: ReferenceData,
        sp: StringProvider
    ): List<Filter<FilterValue>> {
        val accessibilityMap = mapOf(
            "Public" to sp.getString(R.string.accessibility_public),
            "Visitors" to sp.getString(R.string.accessibility_visitors),
            "Employees" to sp.getString(R.string.accessibility_employees),
            "By appointment" to sp.getString(R.string.accessibility_by_appointment),
            "Residents" to sp.getString(R.string.accessibility_residents)
        )
        return listOf(
            BooleanFilter(sp.getString(R.string.filter_free_parking), "freeparking"),
            BooleanFilter(sp.getString(R.string.filter_open_247), "open_247"),
            SliderFilter(
                sp.getString(R.string.filter_min_power), "min_power",
                powerSteps.size - 1,
                mapping = ::mapPower,
                inverseMapping = ::mapPowerInverse,
                unit = "kW"
            ),
            SliderFilter(
                sp.getString(R.string.filter_min_connectors),
                "min_connectors",
                10,
                min = 1
            ),
            MultipleChoiceFilter(
                sp.getString(R.string.filter_accessibility), "accessibilities",
                accessibilityMap, manyChoices = true
            )
        )
    }

    override fun convertFiltersToSQL(
        filters: FilterValues,
        referenceData: ReferenceData
    ): FiltersSQLQuery {
        if (filters.isEmpty()) return FiltersSQLQuery("",
            requiresChargepointQuery = false,
            requiresChargeCardQuery = false
        )

        var requiresChargepointQuery = false
        val result = StringBuilder()

        if (filters.getBooleanValue("freeparking") == true) {
            result.append(" AND freeparking IS 1")
        }

        if (filters.getBooleanValue("open_247") == true) {
            result.append(" AND twentyfourSeven IS 1")
        }

        val minPower = filters.getSliderValue("min_power")
        if (minPower != null && minPower > 0) {
            result.append(" AND json_extract(cp.value, '$.power') >= $minPower")
            requiresChargepointQuery = true
        }

        val minConnectors = filters.getSliderValue("min_connectors")
        if (minConnectors != null && minConnectors > 1) {
            result.append(" GROUP BY ChargeLocation.id HAVING SUM(json_extract(cp.value, '$.count')) >= $minConnectors")
            requiresChargepointQuery = true
        }

        val accessibilities = filters.getMultipleChoiceValue("accessibilities")
        if (accessibilities != null && !accessibilities.all) {
            val accessibilitiesList = accessibilities.values.joinToString(",") {
                DatabaseUtils.sqlEscapeString(it)
            }
            result.append(" AND accessibility IN (${accessibilitiesList})")
        }

        return FiltersSQLQuery(result.toString(), requiresChargepointQuery, false)
    }

    override fun filteringInSQLRequiresDetails(filters: FilterValues): Boolean {
        return false
    }

}