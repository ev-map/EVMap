package net.vonforst.evmap.api.openstreetmap

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
import net.vonforst.evmap.api.FullDownloadResult
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.mapPower
import net.vonforst.evmap.api.mapPowerInverse
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.openchargemap.ZonedDateTimeAdapter
import net.vonforst.evmap.api.powerSteps
import net.vonforst.evmap.model.BooleanFilter
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
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
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import java.io.IOException
import java.time.Duration

interface OpenStreetMapApi {
    @GET("charging-stations-osm.json")
    suspend fun getAllChargingStations(): Response<OSMDocument>

    companion object {
        private val moshi = Moshi.Builder()
            .add(ZonedDateTimeAdapter())
            .add(InstantAdapter())
            .build()

        fun create(
            baseurl: String = "https://osm.ev-map.app/"
        ): OpenStreetMapApi {
            val client = OkHttpClient.Builder().apply {
                if (BuildConfig.DEBUG) addDebugInterceptors()
            }.build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseurl)
                .addConverterFactory(OSMConverterFactory(moshi))
                .client(client)
                .build()
            return retrofit.create(OpenStreetMapApi::class.java)
        }
    }

}

class OpenStreetMapApiWrapper(baseurl: String = "https://osm.ev-map.app/") :
    ChargepointApi<OSMReferenceData> {
    override val name = "OpenStreetMap"
    override val id = "openstreetmap"
    override val cacheLimit = Duration.ofDays(300L)
    override val supportsOnlineQueries = false
    override val supportsFullDownload = true

    val api = OpenStreetMapApi.create(baseurl)

    override suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<ChargepointList> {
        throw NotImplementedError()
    }

    override suspend fun getChargepointsRadius(
        referenceData: ReferenceData,
        location: LatLng,
        radius: Int,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<ChargepointList> {
        throw NotImplementedError()
    }

    override suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation> {
        throw NotImplementedError()
    }

    override suspend fun getReferenceData(): Resource<OSMReferenceData> {
        throw NotImplementedError()
    }

    override fun getFilters(
        referenceData: ReferenceData,
        sp: StringProvider
    ): List<Filter<FilterValue>> {

        val plugs = listOf(
            Chargepoint.TYPE_1,
            Chargepoint.CCS_TYPE_1,
            Chargepoint.TYPE_2_SOCKET,
            Chargepoint.TYPE_2_PLUG,
            Chargepoint.CCS_TYPE_2,
            Chargepoint.CHADEMO,
            Chargepoint.SUPERCHARGER,
            Chargepoint.CEE_BLAU,
            Chargepoint.CEE_ROT,
            Chargepoint.SCHUKO
        )
        val plugMap = plugs.associateWith { plug ->
            nameForPlugType(sp, plug)
        }

        val refData = referenceData as OSMReferenceData
        val networkMap = refData.networks.associateWith { it }

        return listOf(
            BooleanFilter(sp.getString(R.string.filter_free), "freecharging"),
            BooleanFilter(sp.getString(R.string.filter_free_parking), "freeparking"),
            BooleanFilter(sp.getString(R.string.filter_open_247), "open_247"),
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
                    Chargepoint.TYPE_1,
                    Chargepoint.TYPE_2_SOCKET,
                    Chargepoint.TYPE_2_PLUG,
                    Chargepoint.CCS_TYPE_1,
                    Chargepoint.CCS_TYPE_2,
                    Chargepoint.CHADEMO
                ),
                manyChoices = true
            ),
            MultipleChoiceFilter(
                sp.getString(R.string.filter_networks), "networks",
                networkMap, manyChoices = true
            ),
            SliderFilter(
                sp.getString(R.string.filter_min_connectors),
                "min_connectors",
                10,
                min = 1
            )
        )
    }

    override fun convertFiltersToSQL(
        filters: FilterValues,
        referenceData: ReferenceData
    ): FiltersSQLQuery {
        if (filters.isEmpty()) return FiltersSQLQuery("", false, false)
        var requiresChargepointQuery = false

        val result = StringBuilder()
        if (filters.getBooleanValue("freecharging") == true) {
            result.append(" AND freecharging IS 1")
        }
        if (filters.getBooleanValue("freeparking") == true) {
            result.append(" AND freeparking IS 1")
        }
        if (filters.getBooleanValue("open_247") == true) {
            result.append(" AND twentyfourSeven IS 1")
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
                    DatabaseUtils.sqlEscapeString(it)
                }
            }
            result.append(" AND json_extract(cp.value, '$.type') IN (${connectorsList})")
            requiresChargepointQuery = true
        }

        val minConnectors = filters.getSliderValue("min_connectors")
        if (minConnectors != null && minConnectors > 1) {
            result.append(" GROUP BY ChargeLocation.id HAVING SUM(json_extract(cp.value, '$.count')) >= $minConnectors")
            requiresChargepointQuery = true
        }

        val networks = filters.getMultipleChoiceValue("networks")
        if (networks != null && !networks.all) {
            val networksList = if (networks.values.size == 0) {
                ""
            } else {
                networks.values.joinToString(",") { DatabaseUtils.sqlEscapeString(it) }
            }
            result.append(" AND network IN (${networksList})")
        }

        return FiltersSQLQuery(result.toString(), requiresChargepointQuery, false)
    }

    override fun filteringInSQLRequiresDetails(filters: FilterValues): Boolean {
        return true
    }

    override suspend fun fullDownload(): FullDownloadResult<OSMReferenceData> {
        val response = api.getAllChargingStations()
        if (!response.isSuccessful) {
            throw IOException(response.message())
        } else {
            val body = response.body()!!
            return OSMFullDownloadResult(body)
        }
    }
}

data class OSMReferenceData(val networks: List<String>) : ReferenceData()

class OSMFullDownloadResult(private val body: OSMDocument) : FullDownloadResult<OSMReferenceData> {
    private var downloadProgress = 0f
    private var refData: OSMReferenceData? = null

    override val chargers: Sequence<ChargeLocation>
        get() {
            val time = body.timestamp
            val networks = mutableListOf<String>()

            return sequence {
                body.elements.forEachIndexed { i, it ->
                    val charger = it.convert(time)
                    yield(charger)
                    downloadProgress = i.toFloat() / body.count
                    charger.network?.let { networks.add(it) }
                }
                refData = OSMReferenceData(networks)
            }
        }
    override val progress: Float
        get() = downloadProgress
    override val referenceData: OSMReferenceData
        get() = refData
            ?: throw UnsupportedOperationException("referenceData is only available once download is complete")

}
