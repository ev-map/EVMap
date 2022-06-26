package net.vonforst.evmap.api.goingelectric

import android.content.Context
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
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
import retrofit2.http.*
import java.io.IOException

interface GoingElectricApi {
    @FormUrlEncoded
    @POST("chargepoints/")
    suspend fun getChargepoints(
        @Field("sw_lat") sw_lat: Double, @Field("sw_lng") sw_lng: Double,
        @Field("ne_lat") ne_lat: Double, @Field("ne_lng") ne_lng: Double,
        @Field("zoom") zoom: Float,
        @Field("clustering") clustering: Boolean = false,
        @Field("cluster_distance") clusterDistance: Int? = null,
        @Field("freecharging") freecharging: Boolean = false,
        @Field("freeparking") freeparking: Boolean = false,
        @Field("min_power") minPower: Int = 0,
        @Field("plugs") plugs: String? = null,
        @Field("chargecards") chargecards: String? = null,
        @Field("networks") networks: String? = null,
        @Field("categories") categories: String? = null,
        @Field("startkey") startkey: Int? = null,
        @Field("open_twentyfourseven") open247: Boolean = false,
        @Field("barrierfree") barrierfree: Boolean = false,
        @Field("exclude_faults") excludeFaults: Boolean = false
    ): Response<GEChargepointList>

    @FormUrlEncoded
    @POST("chargepoints/")
    suspend fun getChargepointsRadius(
        @Field("lat") lat: Double, @Field("lng") lng: Double,
        @Field("radius") radius: Int,
        @Field("zoom") zoom: Float,
        @Field("orderby") orderby: String = "distance",
        @Field("clustering") clustering: Boolean = false,
        @Field("cluster_distance") clusterDistance: Int? = null,
        @Field("freecharging") freecharging: Boolean = false,
        @Field("freeparking") freeparking: Boolean = false,
        @Field("min_power") minPower: Int = 0,
        @Field("plugs") plugs: String? = null,
        @Field("chargecards") chargecards: String? = null,
        @Field("networks") networks: String? = null,
        @Field("categories") categories: String? = null,
        @Field("startkey") startkey: Int? = null,
        @Field("open_twentyfourseven") open247: Boolean = false,
        @Field("barrierfree") barrierfree: Boolean = false,
        @Field("exclude_faults") excludeFaults: Boolean = false
    ): Response<GEChargepointList>

    @GET("chargepoints/")
    suspend fun getChargepointDetail(@Query("ge_id") id: Long): Response<GEChargepointList>

    @GET("chargepoints/pluglist/")
    suspend fun getPlugs(): Response<GEStringList>

    @GET("chargepoints/networklist/")
    suspend fun getNetworks(): Response<GEStringList>

    @GET("chargepoints/chargecardlist/")
    suspend fun getChargeCards(): Response<GEChargeCardList>

    companion object {
        private val cacheSize = 10L * 1024 * 1024 // 10MB

        private val moshi = Moshi.Builder()
            .add(ChargepointListItemJsonAdapterFactory())
            .add(JsonObjectOrFalseAdapter.Factory())
            .add(HoursAdapter())
            .add(InstantAdapter())
            .build()

        fun create(
            apikey: String,
            baseurl: String = "https://api.goingelectric.de",
            context: Context? = null
        ): GoingElectricApi {
            val client = OkHttpClient.Builder().apply {
                addInterceptor { chain ->
                    // add API key to every request
                    var original = chain.request()
                    val url = original.url.newBuilder().addQueryParameter("key", apikey).build()
                    original = original.newBuilder().url(url).build()
                    chain.proceed(original)
                }
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(StethoInterceptor())
                }
                if (context != null) {
                    cache(Cache(context.getCacheDir(), cacheSize))
                }
            }.build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseurl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
            return retrofit.create(GoingElectricApi::class.java)
        }
    }
}

class GoingElectricApiWrapper(
    val apikey: String,
    baseurl: String = "https://api.goingelectric.de",
    context: Context? = null
) : ChargepointApi<GEReferenceData> {
    private val clusterThreshold = 11f
    val api = GoingElectricApi.create(apikey, baseurl, context)

    override fun getName() = "GoingElectric.de"

    override suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        filters: FilterValues?
    ): Resource<List<ChargepointListItem>> {
        val freecharging = filters?.getBooleanValue("freecharging")
        val freeparking = filters?.getBooleanValue("freeparking")
        val open247 = filters?.getBooleanValue("open_247")
        val barrierfree = filters?.getBooleanValue("barrierfree")
        val excludeFaults = filters?.getBooleanValue("exclude_faults")
        val minPower = filters?.getSliderValue("min_power")
        val minConnectors = filters?.getSliderValue("min_connectors")

        var connectorsVal = filters?.getMultipleChoiceValue("connectors")
        if (connectorsVal != null && connectorsVal.values.isEmpty() && !connectorsVal.all) {
            // no connectors chosen
            return Resource.success(emptyList())
        }
        val connectors = formatMultipleChoice(connectorsVal)

        val chargeCardsVal = filters?.getMultipleChoiceValue("chargecards")
        if (chargeCardsVal != null && chargeCardsVal.values.isEmpty() && !chargeCardsVal.all) {
            // no chargeCards chosen
            return Resource.success(emptyList())
        }
        val chargeCards = formatMultipleChoice(chargeCardsVal)

        val networksVal = filters?.getMultipleChoiceValue("networks")
        if (networksVal != null && networksVal.values.isEmpty() && !networksVal.all) {
            // no networks chosen
            return Resource.success(emptyList())
        }
        val networks = formatMultipleChoice(networksVal)

        val categoriesVal = filters?.getMultipleChoiceValue("categories")
        if (categoriesVal != null && categoriesVal.values.isEmpty() && !categoriesVal.all) {
            // no categories chosen
            return Resource.success(emptyList())
        }
        val categories = formatMultipleChoice(categoriesVal)

        // do not use clustering if filters need to be applied locally.
        val useClustering = zoom < clusterThreshold
        val geClusteringAvailable = minConnectors == null || minConnectors <= 1
        val useGeClustering = useClustering && geClusteringAvailable
        val clusterDistance = if (useClustering) getClusterDistance(zoom) else null

        var startkey: Int? = null
        val data = mutableListOf<GEChargepointListItem>()
        do {
            // load all pages of the response
            try {
                val response = api.getChargepoints(
                    bounds.southwest.latitude,
                    bounds.southwest.longitude,
                    bounds.northeast.latitude,
                    bounds.northeast.longitude,
                    clustering = useGeClustering,
                    zoom = zoom,
                    clusterDistance = clusterDistance,
                    freecharging = freecharging ?: false,
                    minPower = minPower ?: 0,
                    freeparking = freeparking ?: false,
                    open247 = open247 ?: false,
                    barrierfree = barrierfree ?: false,
                    excludeFaults = excludeFaults ?: false,
                    plugs = connectors,
                    chargecards = chargeCards,
                    networks = networks,
                    categories = categories,
                    startkey = startkey
                )
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    return Resource.error(response.message(), null)
                } else {
                    val body = response.body()!!
                    data.addAll(body.chargelocations)
                    startkey = body.startkey
                }
            } catch (e: IOException) {
                return Resource.error(e.message, null)
            }
        } while (startkey != null && startkey < 10000)

        var result = postprocessResult(data, minPower, connectorsVal, minConnectors, zoom)

        return Resource.success(result)
    }

    private fun formatMultipleChoice(value: MultipleChoiceFilterValue?) =
        if (value == null || value.all) null else value.values.joinToString(",")

    override suspend fun getChargepointsRadius(
        referenceData: ReferenceData,
        location: LatLng,
        radius: Int,
        zoom: Float,
        filters: FilterValues?
    ): Resource<List<ChargepointListItem>> {
        val freecharging = filters?.getBooleanValue("freecharging")
        val freeparking = filters?.getBooleanValue("freeparking")
        val open247 = filters?.getBooleanValue("open_247")
        val barrierfree = filters?.getBooleanValue("barrierfree")
        val excludeFaults = filters?.getBooleanValue("exclude_faults")
        val minPower = filters?.getSliderValue("min_power")
        val minConnectors = filters?.getSliderValue("min_connectors")

        var connectorsVal = filters?.getMultipleChoiceValue("connectors")
        if (connectorsVal != null && connectorsVal.values.isEmpty() && !connectorsVal.all) {
            // no connectors chosen
            return Resource.success(emptyList())
        }
        val connectors = formatMultipleChoice(connectorsVal)

        val chargeCardsVal = filters?.getMultipleChoiceValue("chargecards")
        if (chargeCardsVal != null && chargeCardsVal.values.isEmpty() && !chargeCardsVal.all) {
            // no chargeCards chosen
            return Resource.success(emptyList())
        }
        val chargeCards = formatMultipleChoice(chargeCardsVal)

        val networksVal = filters?.getMultipleChoiceValue("networks")
        if (networksVal != null && networksVal.values.isEmpty() && !networksVal.all) {
            // no networks chosen
            return Resource.success(emptyList())
        }
        val networks = formatMultipleChoice(networksVal)

        val categoriesVal = filters?.getMultipleChoiceValue("categories")
        if (categoriesVal != null && categoriesVal.values.isEmpty() && !categoriesVal.all) {
            // no categories chosen
            return Resource.success(emptyList())
        }
        val categories = formatMultipleChoice(categoriesVal)

        // do not use clustering if filters need to be applied locally.
        val useClustering = zoom < clusterThreshold
        val geClusteringAvailable = minConnectors == null || minConnectors <= 1
        val useGeClustering = useClustering && geClusteringAvailable
        val clusterDistance = if (useClustering) getClusterDistance(zoom) else null

        var startkey: Int? = null
        val data = mutableListOf<GEChargepointListItem>()
        do {
            // load all pages of the response
            try {
                val response = api.getChargepointsRadius(
                    location.latitude, location.longitude, radius,
                    clustering = useGeClustering,
                    zoom = zoom,
                    clusterDistance = clusterDistance,
                    freecharging = freecharging ?: false,
                    minPower = minPower ?: 0,
                    freeparking = freeparking ?: false,
                    open247 = open247 ?: false,
                    barrierfree = barrierfree ?: false,
                    excludeFaults = excludeFaults ?: false,
                    plugs = connectors,
                    chargecards = chargeCards,
                    networks = networks,
                    categories = categories,
                    startkey = startkey
                )
                if (!response.isSuccessful || response.body()!!.status != "ok") {
                    return Resource.error(response.message(), null)
                } else {
                    val body = response.body()!!
                    data.addAll(body.chargelocations)
                    startkey = body.startkey
                }
            } catch (e: IOException) {
                return Resource.error(e.message, null)
            }
        } while (startkey != null && startkey < 10000)

        val result = postprocessResult(data, minPower, connectorsVal, minConnectors, zoom)
        return Resource.success(result)
    }

    private fun postprocessResult(
        chargers: List<GEChargepointListItem>,
        minPower: Int?,
        connectorsVal: MultipleChoiceFilterValue?,
        minConnectors: Int?,
        zoom: Float
    ): List<ChargepointListItem> {
        // apply filters which GoingElectric does not support natively
        var result = chargers.filter { it ->
            if (it is GEChargeLocation) {
                it.chargepoints
                    .filter { it.power >= (minPower ?: 0) }
                    .filter { if (connectorsVal != null && !connectorsVal.all) it.type in connectorsVal.values else true }
                    .sumOf { it.count } >= (minConnectors ?: 0)
            } else {
                true
            }
        }.map { it.convert(apikey, false) }

        // apply clustering
        val useClustering = zoom < clusterThreshold
        val geClusteringAvailable = minConnectors == null || minConnectors <= 1
        val clusterDistance = if (useClustering) getClusterDistance(zoom) else null
        if (!geClusteringAvailable && useClustering) {
            // apply local clustering if server side clustering is not available
            Dispatchers.IO.run {
                result = cluster(result, zoom, clusterDistance!!)
            }
        }
        return result
    }

    override suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation> {
        try {
            val response = api.getChargepointDetail(id)
            return if (response.isSuccessful && response.body()!!.status == "ok" && response.body()!!.chargelocations.size == 1) {
                Resource.success(
                    (response.body()!!.chargelocations[0] as GEChargeLocation).convert(
                        apikey, true
                    )
                )
            } else {
                Resource.error(response.message(), null)
            }
        } catch (e: IOException) {
            return Resource.error(e.message, null)
        }
    }

    override suspend fun getReferenceData(): Resource<GEReferenceData> =
        withContext(Dispatchers.IO) {
            supervisorScope {
                try {
                    val plugs = async { api.getPlugs() }
                    val chargeCards = async { api.getChargeCards() }
                    val networks = async { api.getNetworks() }

                    val plugsResponse = plugs.await()
                    val chargeCardsResponse = chargeCards.await()
                    val networksResponse = networks.await()

                    val responses = listOf(plugsResponse, chargeCardsResponse, networksResponse)

                    if (responses.map { it.isSuccessful }.all { it }) {
                        Resource.success(
                            GEReferenceData(
                                plugsResponse.body()!!.result,
                                networksResponse.body()!!.result,
                                chargeCardsResponse.body()!!.result
                            )
                        )
                    } else {
                        Resource.error(responses.find { !it.isSuccessful }!!.message(), null)
                    }
                } catch (e: IOException) {
                    Resource.error(e.message, null)
                }
            }
        }

    override fun getFilters(
        referenceData: ReferenceData,
        sp: StringProvider
    ): List<Filter<FilterValue>> {
        val referenceData = referenceData as GEReferenceData
        val plugs = referenceData.plugs
        val networks = referenceData.networks
        val chargeCards = referenceData.chargecards

        val plugMap = plugs.map { plug ->
            plug to nameForPlugType(sp, GEChargepoint.convertTypeFromGE(plug))
        }.toMap()
        val networkMap = networks.map { it to it }.toMap()
        val chargecardMap = chargeCards.map { it.id.toString() to it.name }.toMap()
        val categoryMap = mapOf(
            "Autohaus" to sp.getString(R.string.category_car_dealership),
            "Autobahnraststätte" to sp.getString(R.string.category_service_on_motorway),
            "Autohof" to sp.getString(R.string.category_service_off_motorway),
            "Bahnhof" to sp.getString(R.string.category_railway_station),
            "Behörde" to sp.getString(R.string.category_public_authorities),
            "Campingplatz" to sp.getString(R.string.category_camping),
            "Einkaufszentrum" to sp.getString(R.string.category_shopping_mall),
            "Ferienwohnung" to sp.getString(R.string.category_holiday_home),
            "Flughafen" to sp.getString(R.string.category_airport),
            "Freizeitpark" to sp.getString(R.string.category_amusement_park),
            "Hotel" to sp.getString(R.string.category_hotel),
            "Kino" to sp.getString(R.string.category_cinema),
            "Kirche" to sp.getString(R.string.category_church),
            "Krankenhaus" to sp.getString(R.string.category_hospital),
            "Museum" to sp.getString(R.string.category_museum),
            "Parkhaus" to sp.getString(R.string.category_parking_multi),
            "Parkplatz" to sp.getString(R.string.category_parking),
            "Privater Ladepunkt" to sp.getString(R.string.category_private_charger),
            "Rastplatz" to sp.getString(R.string.category_rest_area),
            "Restaurant" to sp.getString(R.string.category_restaurant),
            "Schwimmbad" to sp.getString(R.string.category_swimming_pool),
            "Supermarkt" to sp.getString(R.string.category_supermarket),
            "Tankstelle" to sp.getString(R.string.category_petrol_station),
            "Tiefgarage" to sp.getString(R.string.category_parking_underground),
            "Tierpark" to sp.getString(R.string.category_zoo),
            "Wohnmobilstellplatz" to sp.getString(R.string.category_caravan_site)
        )
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
                commonChoices = listOf(
                    Chargepoint.TYPE_2_UNKNOWN,
                    Chargepoint.CCS_UNKNOWN,
                    Chargepoint.CHADEMO
                ).map { GEChargepoint.convertTypeToGE(it)!! }.toSet(),
                manyChoices = true
            ),
            SliderFilter(
                sp.getString(R.string.filter_min_connectors),
                "min_connectors",
                10,
                min = 1
            ),
            MultipleChoiceFilter(
                sp.getString(R.string.filter_networks), "networks",
                networkMap, manyChoices = true
            ),
            MultipleChoiceFilter(
                sp.getString(R.string.categories), "categories",
                categoryMap,
                manyChoices = true
            ),
            BooleanFilter(sp.getString(R.string.filter_barrierfree), "barrierfree"),
            MultipleChoiceFilter(
                sp.getString(R.string.filter_chargecards), "chargecards",
                chargecardMap, manyChoices = true
            ),
            BooleanFilter(sp.getString(R.string.filter_exclude_faults), "exclude_faults")
        )
    }
}

