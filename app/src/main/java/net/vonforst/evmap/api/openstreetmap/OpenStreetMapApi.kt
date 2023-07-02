package net.vonforst.evmap.api.openstreetmap

import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.squareup.moshi.Moshi
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.addDebugInterceptors
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.ChargepointList
import net.vonforst.evmap.api.FiltersSQLQuery
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.openchargemap.ZonedDateTimeAdapter
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Filter
import net.vonforst.evmap.model.FilterValue
import net.vonforst.evmap.model.FilterValues
import net.vonforst.evmap.model.ReferenceData
import net.vonforst.evmap.viewmodel.Resource
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.io.IOException
import java.time.Duration
import java.time.Instant

interface OpenStreetMapApi {
    @GET("charging-stations-osm.json")
    suspend fun getAllChargingStations(): Response<OSMDocument>

    companion object {
        private val moshi = Moshi.Builder()
            .add(ZonedDateTimeAdapter())
            .add(InstantAdapter())
            .build()

        fun create(
            baseurl: String = "https://evmap-dev.vonforst.net"
        ): OpenStreetMapApi {
            val client = OkHttpClient.Builder().apply {
                if (BuildConfig.DEBUG) addDebugInterceptors()
            }.build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseurl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
            return retrofit.create(OpenStreetMapApi::class.java)
        }
    }

}

class OpenStreetMapApiWrapper(baseurl: String = "https://evmap-dev.vonforst.net") :
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
        TODO("Not yet implemented")
    }

    override fun getFilters(
        referenceData: ReferenceData,
        sp: StringProvider
    ): List<Filter<FilterValue>> {
        return emptyList()
    }

    override fun convertFiltersToSQL(
        filters: FilterValues,
        referenceData: ReferenceData
    ): FiltersSQLQuery {
        TODO("Not yet implemented")
    }

    override fun filteringInSQLRequiresDetails(filters: FilterValues): Boolean {
        return true
    }

    override suspend fun fullDownload(referenceData: ReferenceData): List<ChargeLocation> {
        val response = api.getAllChargingStations()
        if (!response.isSuccessful) {
            throw IOException(response.message())
        } else {
            val body = response.body()!!
            val time = body.timestamp
            return body.elements.map { it.convert(time) }
        }
    }
}

data class OSMReferenceData(val test: String) : ReferenceData()
