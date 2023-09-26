package net.vonforst.evmap.api.openchargemap

import android.content.Context
import android.database.DatabaseUtils
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.squareup.moshi.Moshi
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.addDebugInterceptors
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.ChargepointList
import net.vonforst.evmap.api.FiltersSQLQuery
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
import net.vonforst.evmap.model.MultipleChoiceFilterValue
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
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.time.Duration

private const val maxResults = 3000

interface OpenChargeMapApi {
    @GET("poi/")
    suspend fun getChargepoints(
        @Query("boundingbox") boundingbox: OCMBoundingBox,
        @Query("connectiontypeid") plugs: String? = null,
        @Query("minpowerkw") minPower: Double? = null,
        @Query("operatorid") operators: String? = null,
        @Query("statustypeid") statusType: String? = null,
        @Query("maxresults") maxresults: Int = maxResults,
        @Query("compact") compact: Boolean = true,
        @Query("verbose") verbose: Boolean = false
    ): Response<List<OCMChargepoint>>

    @GET("poi/")
    suspend fun getChargepointsRadius(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("distance") distance: Double,
        @Query("distanceunit") distanceUnit: String = "KM",
        @Query("connectiontypeid") plugs: String? = null,
        @Query("minpowerkw") minPower: Double? = null,
        @Query("operatorid") operators: String? = null,
        @Query("statustypeid") statusType: String? = null,
        @Query("maxresults") maxresults: Int = maxResults,
        @Query("compact") compact: Boolean = true,
        @Query("verbose") verbose: Boolean = false
    ): Response<List<OCMChargepoint>>

    @GET("poi/")
    suspend fun getChargepointDetail(
        @Query("chargepointid") id: Long,
        @Query("includecomments") includeComments: Boolean = true,
        @Query("compact") compact: Boolean = false,
        @Query("verbose") verbose: Boolean = false
    ): Response<List<OCMChargepoint>>

    @GET("referencedata/")
    suspend fun getReferenceData(): Response<OCMReferenceData>

    companion object {
        private val cacheSize = 10L * 1024 * 1024 // 10MB

        private val moshi = Moshi.Builder()
            .add(ZonedDateTimeAdapter())
            .build()

        fun create(
            apikey: String,
            baseurl: String = "https://api.openchargemap.io/v3/",
            context: Context? = null
        ): OpenChargeMapApi {
            val client = OkHttpClient.Builder().apply {
                addInterceptor { chain ->
                    // add API key to every request
                    val original = chain.request()
                    val new = original.newBuilder()
                        .header("X-API-Key", apikey)
                        .build()
                    chain.proceed(new)
                }
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
            return retrofit.create(OpenChargeMapApi::class.java)
        }
    }
}

class OpenChargeMapApiWrapper(
    apikey: String,
    baseurl: String = "https://api.openchargemap.io/v3/",
    context: Context? = null
) : ChargepointApi<OCMReferenceData> {
    override val cacheLimit = Duration.ofDays(300L)
    val api = OpenChargeMapApi.create(apikey, baseurl, context)

    override val name = "OpenChargeMap.org"
    override val id = "openchargemap"
    override val supportsOnlineQueries = true
    override val supportsFullDownload = false

    override suspend fun fullDownload(): FullDownloadResult<OCMReferenceData> {
        throw NotImplementedError()
    }

    private fun formatMultipleChoice(value: MultipleChoiceFilterValue?) =
        if (value == null || value.all) null else value.values.joinToString(",")

    override suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?,
    ): Resource<ChargepointList> {
        val refData = referenceData as OCMReferenceData

        val minPower = filters?.getSliderValue("min_power")?.toDouble()
        val minConnectors = filters?.getSliderValue("min_connectors")
        val excludeFaults = filters?.getBooleanValue("exclude_faults")

        val connectorsVal = filters?.getMultipleChoiceValue("connectors")
        if (connectorsVal != null && connectorsVal.values.isEmpty() && !connectorsVal.all) {
            // no connectors chosen
            return Resource.success(ChargepointList.empty())
        }
        val connectors = formatMultipleChoice(connectorsVal)

        val operatorsVal = filters?.getMultipleChoiceValue("operators")
        if (operatorsVal != null && operatorsVal.values.isEmpty() && !operatorsVal.all) {
            // no operators chosen
            return Resource.success(ChargepointList.empty())
        }
        val operators = formatMultipleChoice(operatorsVal)

        try {
            val response = api.getChargepoints(
                OCMBoundingBox(
                    bounds.southwest.latitude, bounds.southwest.longitude,
                    bounds.northeast.latitude, bounds.northeast.longitude
                ),
                minPower = minPower,
                plugs = connectors,
                operators = operators
            )
            if (!response.isSuccessful) {
                return Resource.error(response.message(), null)
            }

            val data = response.body()!!
            val result = postprocessResult(
                data,
                minPower,
                connectorsVal,
                minConnectors,
                excludeFaults,
                refData
            )
            return Resource.success(ChargepointList(result, data.size < maxResults))
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
        val refData = referenceData as OCMReferenceData

        val minPower = filters?.getSliderValue("min_power")?.toDouble()
        val minConnectors = filters?.getSliderValue("min_connectors")
        val excludeFaults = filters?.getBooleanValue("exclude_faults")

        val connectorsVal = filters?.getMultipleChoiceValue("connectors")
        if (connectorsVal != null && connectorsVal.values.isEmpty() && !connectorsVal.all) {
            // no connectors chosen
            return Resource.success(ChargepointList.empty())
        }
        val connectors = formatMultipleChoice(connectorsVal)

        val operatorsVal = filters?.getMultipleChoiceValue("operators")
        if (operatorsVal != null && operatorsVal.values.isEmpty() && !operatorsVal.all) {
            // no operators chosen
            return Resource.success(ChargepointList.empty())
        }
        val operators = formatMultipleChoice(operatorsVal)

        try {
            val response = api.getChargepointsRadius(
                location.latitude, location.longitude,
                radius.toDouble(),
                minPower = minPower,
                plugs = connectors,
                operators = operators
            )
            if (!response.isSuccessful) {
                return Resource.error(response.message(), null)
            }

            val data = response.body()!!
            val result = postprocessResult(
                data,
                minPower,
                connectorsVal,
                minConnectors,
                excludeFaults,
                refData
            )
            return Resource.success(ChargepointList(result, data.size < 499))
        } catch (e: IOException) {
            return Resource.error(e.message, null)
        } catch (e: HttpException) {
            return Resource.error(e.message, null)
        }
    }

    private fun postprocessResult(
        chargers: List<OCMChargepoint>,
        minPower: Double?,
        connectorsVal: MultipleChoiceFilterValue?,
        minConnectors: Int?,
        excludeFaults: Boolean?,
        referenceData: OCMReferenceData
    ): List<ChargepointListItem> {
        // apply filters which OCM does not support natively
        return chargers.filter { it ->
            it.connections
                .filter { it.power == null || it.power >= (minPower ?: 0.0) }
                .filter { if (connectorsVal != null && !connectorsVal.all) it.connectionTypeId in connectorsVal.values.map { it.toLong() } else true }
                .sumOf { it.quantity ?: 1 } >= (minConnectors ?: 0)
        }.filter {
            it.statusTypeId == null || (it.statusTypeId !in removedStatuses && if (excludeFaults == true) it.statusTypeId !in faultStatuses else true)
        }.map { it.convert(referenceData, false) }.distinct()
    }

    override suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation> {
        val refData = referenceData as OCMReferenceData
        try {
            val response = api.getChargepointDetail(id)
            if (response.isSuccessful && response.body()?.size == 1) {
                return Resource.success(response.body()!![0].convert(refData, true))
            } else {
                return Resource.error(response.message(), null)
            }
        } catch (e: IOException) {
            return Resource.error(e.message, null)
        } catch (e: HttpException) {
            return Resource.error(e.message, null)
        }
    }

    override suspend fun getReferenceData(): Resource<OCMReferenceData> {
        try {
            val response = api.getReferenceData()
            if (response.isSuccessful) {
                return Resource.success(response.body()!!)
            } else {
                return Resource.error(response.message(), null)
            }
        } catch (e: IOException) {
            return Resource.error(e.message, null)
        } catch (e: HttpException) {
            return Resource.error(e.message, null)
        }
    }

    override fun getFilters(
        referenceData: ReferenceData,
        sp: StringProvider
    ): List<Filter<FilterValue>> {
        val refData = referenceData as OCMReferenceData

        val operatorsMap = refData.operators.associate { it.id.toString() to it.title }
        val plugMap = refData.connectionTypes.associate { it.id.toString() to it.title }

        return listOf(
            // supported by OCM API
            SliderFilter(
                sp.getString(R.string.filter_min_power), "min_power",
                powerSteps.size - 1,
                mapping = ::mapPower,
                inverseMapping = ::mapPowerInverse,
                unit = "kW"
            ),
            MultipleChoiceFilter(
                sp.getString(R.string.filter_connectors), "connectors",
                plugMap,
                commonChoices = setOf(
                    "1", // Type 1 (J1772)
                    "25", // Type 2 (Socket only)
                    "1036", // Type 2 (Tethered connector)
                    "32", // CCS (Type 1)
                    "33", // CCS (Type 2)
                    "2" // CHAdeMO
                ),
                manyChoices = true
            ),
            MultipleChoiceFilter(
                sp.getString(R.string.filter_operators), "operators",
                operatorsMap, manyChoices = true
            ),
            BooleanFilter(sp.getString(R.string.filter_exclude_faults), "exclude_faults"),

            // local filters
            SliderFilter(
                sp.getString(R.string.filter_min_connectors),
                "min_connectors",
                10,
                min = 1
            ),
        )
    }

    override fun convertFiltersToSQL(
        filters: FilterValues,
        referenceData: ReferenceData
    ): FiltersSQLQuery {
        if (filters.isEmpty()) return FiltersSQLQuery("", false, false)

        val refData = referenceData as OCMReferenceData
        var requiresChargepointQuery = false

        val result = StringBuilder()

        if (filters.getBooleanValue("exclude_faults") == true) {
            result.append(" AND fault_report_description IS NULL AND fault_report_created IS NULL")
        }

        val minPower = filters.getSliderValue("min_power")
        if (minPower != null && minPower > 0) {
            result.append(" AND json_extract(cp.value, '$.power') >= ${minPower}")
            requiresChargepointQuery = true
        }

        val connectors = filters.getMultipleChoiceValue("connectors")
        if (connectors != null && !connectors.all) {
            val connectorsList = if (connectors.values.size == 0) {
                ""
            } else {
                connectors.values.joinToString(",") {
                    DatabaseUtils.sqlEscapeString(
                        OCMConnection.convertConnectionTypeFromOCM(
                            it.toLong(),
                            refData
                        )
                    )
                }
            }
            result.append(" AND json_extract(cp.value, '$.type') IN (${connectorsList})")
            requiresChargepointQuery = true
        }

        val operators = filters.getMultipleChoiceValue("operators")
        if (operators != null && !operators.all) {
            val networksList = if (operators.values.size == 0) {
                ""
            } else {
                operators.values.joinToString(",") { opId ->
                    DatabaseUtils.sqlEscapeString(refData.operators.find { it.id == opId.toLong() }?.title.orEmpty())
                }
            }
            result.append(" AND network IN (${networksList})")
        }

        val minConnectors = filters.getSliderValue("min_connectors")
        if (minConnectors != null && minConnectors > 1) {
            result.append(" GROUP BY ChargeLocation.id HAVING SUM(json_extract(cp.value, '$.count')) >= ${minConnectors}")
            requiresChargepointQuery = true
        }

        return FiltersSQLQuery(result.toString(), requiresChargepointQuery, false)
    }

    override fun filteringInSQLRequiresDetails(filters: FilterValues): Boolean {
        return false
    }

}