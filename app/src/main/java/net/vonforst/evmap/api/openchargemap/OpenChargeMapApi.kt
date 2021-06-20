package net.vonforst.evmap.api.openchargemap

import android.content.Context
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.StringProvider
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
        @Query("compact") compact: Boolean = true,
        @Query("maxresults") maxresults: Int = 100
    ): Response<List<OCMChargepoint>>

    @GET("poi/")
    suspend fun getChargepointDetail(
        @Query("chargepointid") id: Long,
        @Query("compact") compact: Boolean = false
    ): Response<List<OCMChargepoint>>

    @GET("referencedata/")
    suspend fun getReferenceData(): Response<OCMReferenceData>

    companion object {
        private val cacheSize = 10L * 1024 * 1024 // 10MB

        val moshi = Moshi.Builder()
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

    override suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues,
    ): Resource<List<ChargepointListItem>> {
        val referenceData = referenceData as OCMReferenceData
        val response = api.getChargepoints(
            OCMBoundingBox(
                bounds.southwest.latitude, bounds.southwest.longitude,
                bounds.northeast.latitude, bounds.northeast.longitude
            )
        )
        if (!response.isSuccessful) {
            return Resource.error(response.message(), null)
        }

        var result = response.body()!!.map { it.convert(referenceData) }
            .distinct() as List<ChargepointListItem>

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
        return emptyList()
    }

}