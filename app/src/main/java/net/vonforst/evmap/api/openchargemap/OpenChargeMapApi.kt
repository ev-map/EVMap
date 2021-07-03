package net.vonforst.evmap.api.openchargemap

import android.content.Context
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.api.*
import net.vonforst.evmap.model.*
import net.vonforst.evmap.ui.cluster
import net.vonforst.evmap.viewmodel.Resource
import net.vonforst.evmap.viewmodel.getClusterDistance
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenChargeMapApi {
    @GET("poi/")
    suspend fun getChargepoints(
        @Query("boundingbox") boundingbox: OCMBoundingBox,
        @Query("connectiontypeid") plugs: String? = null,
        @Query("minpowerkw") minPower: Double? = null,
        @Query("operatorid") operators: String? = null,
        @Query("compact") compact: Boolean = true,
        @Query("statustypeid") statusType: String? = null,
        @Query("maxresults") maxresults: Int = 100,
    ): Response<List<OCMChargepoint>>

    @GET("poi/")
    suspend fun getChargepointDetail(
        @Query("chargepointid") id: Long,
        @Query("compact") compact: Boolean = false,
        @Query("includecomments") includeComments: Boolean = true
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
                    addNetworkInterceptor(StethoInterceptor())
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
    val api = OpenChargeMapApi.create(apikey, baseurl, context)

    override fun getName() = "OpenChargeMap.org"

    private fun formatMultipleChoice(value: MultipleChoiceFilterValue) =
        if (value.all) null else value.values.joinToString(",")

    // Unknown, Currently Available, Currently In Use, Operational, Partly Operational
    private val noFaultStatuses = "0,10,20,50,75"

    override suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues,
    ): Resource<List<ChargepointListItem>> {
        val referenceData = referenceData as OCMReferenceData

        val minPower = filters.getSliderValue("min_power")!!.toDouble()
        val minConnectors = filters.getSliderValue("min_connectors")!!
        val excludeFaults = filters.getBooleanValue("exclude_faults")!!

        val connectorsVal = filters.getMultipleChoiceValue("connectors")!!
        if (connectorsVal.values.isEmpty() && !connectorsVal.all) {
            // no connectors chosen
            return Resource.success(emptyList())
        }
        val connectors = formatMultipleChoice(connectorsVal)

        val operatorsVal = filters.getMultipleChoiceValue("operators")!!
        if (operatorsVal.values.isEmpty() && !operatorsVal.all) {
            // no operators chosen
            return Resource.success(emptyList())
        }
        val operators = formatMultipleChoice(operatorsVal)

        val response = api.getChargepoints(
            OCMBoundingBox(
                bounds.southwest.latitude, bounds.southwest.longitude,
                bounds.northeast.latitude, bounds.northeast.longitude
            ),
            minPower = minPower,
            plugs = connectors,
            operators = operators,
            statusType = if (excludeFaults) noFaultStatuses else null
        )
        if (!response.isSuccessful) {
            return Resource.error(response.message(), null)
        }

        var result = response.body()!!.filter { it ->
            // apply filters which OCM does not support natively
            it.connections
                .filter { it.power == null || it.power >= minPower }
                .filter { if (!connectorsVal.all) it.connectionTypeId in connectorsVal.values.map { it.toLong() } else true }
                .sumOf { it.quantity ?: 0 } >= minConnectors
        }.map { it.convert(referenceData) }.distinct() as List<ChargepointListItem>

        val useClustering = zoom < 13
        val clusterDistance = if (useClustering) getClusterDistance(zoom) else null
        if (useClustering) {
            Dispatchers.IO.run {
                result = cluster(result, zoom, clusterDistance!!)
            }
        }

        return Resource.success(result)
    }

    override suspend fun getChargepointsRadius(
        referenceData: ReferenceData,
        location: LatLng,
        radius: Int,
        zoom: Float,
        filters: FilterValues
    ): Resource<List<ChargepointListItem>> {
        TODO("Not yet implemented")
    }

    override suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation> {
        val referenceData = referenceData as OCMReferenceData
        val response = api.getChargepointDetail(id)
        if (response.isSuccessful) {
            return Resource.success(response.body()!![0].convert(referenceData))
        } else {
            return Resource.error(response.message(), null)
        }
    }

    override suspend fun getReferenceData(): Resource<OCMReferenceData> {
        val response = api.getReferenceData()
        if (response.isSuccessful) {
            return Resource.success(response.body()!!)
        } else {
            return Resource.error(response.message(), null)
        }
    }

    override fun getFilters(
        referenceData: ReferenceData,
        sp: StringProvider
    ): List<Filter<FilterValue>> {
        val referenceData = referenceData as OCMReferenceData

        val operatorsMap = referenceData.operators.map { it.id.toString() to it.title }.toMap()
        val plugMap = referenceData.connectionTypes.map { it.id.toString() to it.title }.toMap()

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

}