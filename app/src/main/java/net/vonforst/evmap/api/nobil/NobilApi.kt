package net.vonforst.evmap.api.nobil

import android.content.Context
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.squareup.moshi.Moshi
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.addDebugInterceptors
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.ChargepointList
import net.vonforst.evmap.api.FiltersSQLQuery
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.ChargepointListItem
import net.vonforst.evmap.model.Filter
import net.vonforst.evmap.model.FilterValue
import net.vonforst.evmap.model.FilterValues
import net.vonforst.evmap.model.ReferenceData
import net.vonforst.evmap.model.getBooleanValue
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
            baseurl: String = "https://nobil.no/api/server/",
            context: Context? = null
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
    override val cacheLimit = Duration.ofDays(300L)
    val api = NobilApi.create(baseurl, context)

    override val name = "Nobil"
    override val id = "nobil"

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
                data.chargerStations,
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
                data.chargerStations,
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
        chargerStations: List<NobilChargerStation>,
        filters: FilterValues?
    ): List<ChargepointListItem> {
        return chargerStations.map { it.convert() }.distinct()
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
        return listOf()
    }

    override fun convertFiltersToSQL(
        filters: FilterValues,
        referenceData: ReferenceData
    ): FiltersSQLQuery {
        if (filters.isEmpty()) return FiltersSQLQuery("", false, false)

        val result = StringBuilder()

        if (filters.getBooleanValue("freeparking") == true) {
            result.append(" AND freeparking IS 1")
        }
        if (filters.getBooleanValue("open_247") == true) {
            result.append(" AND twentyfourSeven IS 1")
        }

        val minConnectors = filters.getSliderValue("min_connectors")
        if (minConnectors != null && minConnectors > 1) {
            result.append(" GROUP BY ChargeLocation.id HAVING SUM(json_extract(cp.value, '$.count')) >= ${minConnectors}")
        }

        return FiltersSQLQuery(result.toString(), false, false)
    }

    override fun filteringInSQLRequiresDetails(filters: FilterValues): Boolean {
        return false
    }

}