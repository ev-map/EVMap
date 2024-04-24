package net.vonforst.evmap.api.goingelectric

import android.content.Context
import android.database.DatabaseUtils
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.LatLngBounds
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.addDebugInterceptors
import net.vonforst.evmap.api.ChargepointApi
import net.vonforst.evmap.api.ChargepointList
import net.vonforst.evmap.api.FiltersSQLQuery
import net.vonforst.evmap.api.StringProvider
import net.vonforst.evmap.api.mapPower
import net.vonforst.evmap.api.mapPowerInverse
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.powerSteps
import net.vonforst.evmap.model.BooleanFilter
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
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
import net.vonforst.evmap.viewmodel.getClusterDistance
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.time.Duration

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
            return retrofit.create(GoingElectricApi::class.java)
        }
    }
}

private const val STATUS_OK = "ok"

class GoingElectricApiWrapper(
    val apikey: String,
    baseurl: String = "https://api.goingelectric.de",
    context: Context? = null
) : ChargepointApi<GEReferenceData> {
    val api = GoingElectricApi.create(apikey, baseurl, context)

    override val name = "GoingElectric.de"
    override val id = "goingelectric"
    override val cacheLimit = Duration.ofDays(1)

    override suspend fun getChargepoints(
        referenceData: ReferenceData,
        bounds: LatLngBounds,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<ChargepointList> {
        val freecharging = filters?.getBooleanValue("freecharging")
        val freeparking = filters?.getBooleanValue("freeparking")
        val open247 = filters?.getBooleanValue("open_247")
        val barrierfree = filters?.getBooleanValue("barrierfree")
        val excludeFaults = filters?.getBooleanValue("exclude_faults")
        val minPower = filters?.getSliderValue("min_power")
        val minConnectors = filters?.getSliderValue("min_connectors")

        val connectorsVal = filters?.getMultipleChoiceValue("connectors")
        if (connectorsVal != null && connectorsVal.values.isEmpty() && !connectorsVal.all) {
            // no connectors chosen
            return Resource.success(ChargepointList.empty())
        }
        if (connectorsVal != null && connectorsVal.values.contains("CCS")) {
            // see note about Tesla Supercharger CCS filter in getFilters below
            connectorsVal.values.add("Tesla Supercharger CCS")
        }
        val connectors = formatMultipleChoice(connectorsVal)

        val chargeCardsVal = filters?.getMultipleChoiceValue("chargecards")
        if (chargeCardsVal != null && chargeCardsVal.values.isEmpty() && !chargeCardsVal.all) {
            // no chargeCards chosen
            return Resource.success(ChargepointList.empty())
        }
        val chargeCards = formatMultipleChoice(chargeCardsVal)

        val networksVal = filters?.getMultipleChoiceValue("networks")
        if (networksVal != null && networksVal.values.isEmpty() && !networksVal.all) {
            // no networks chosen
            return Resource.success(ChargepointList.empty())
        }
        val networks = formatMultipleChoice(networksVal)

        val categoriesVal = filters?.getMultipleChoiceValue("categories")
        if (categoriesVal != null && categoriesVal.values.isEmpty() && !categoriesVal.all) {
            // no categories chosen
            return Resource.success(ChargepointList.empty())
        }
        val categories = formatMultipleChoice(categoriesVal)

        // do not use clustering if filters need to be applied locally.
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
                if (!response.isSuccessful || response.body()!!.status != STATUS_OK) {
                    return Resource.error(response.message(), null)
                } else {
                    val body = response.body()!!
                    data.addAll(body.chargelocations!!)
                    startkey = body.startkey
                }
            } catch (e: IOException) {
                return Resource.error(e.message, null)
            } catch (e: HttpException) {
                return Resource.error(e.message, null)
            }
        } while (startkey != null && startkey < 10000)

        val result = postprocessResult(data, filters)

        return Resource.success(ChargepointList(result, startkey == null))
    }

    private fun formatMultipleChoice(value: MultipleChoiceFilterValue?) =
        if (value == null || value.all) null else value.values.joinToString(",")

    override suspend fun getChargepointsRadius(
        referenceData: ReferenceData,
        location: LatLng,
        radius: Int,
        zoom: Float,
        useClustering: Boolean,
        filters: FilterValues?
    ): Resource<ChargepointList> {
        val freecharging = filters?.getBooleanValue("freecharging")
        val freeparking = filters?.getBooleanValue("freeparking")
        val open247 = filters?.getBooleanValue("open_247")
        val barrierfree = filters?.getBooleanValue("barrierfree")
        val excludeFaults = filters?.getBooleanValue("exclude_faults")
        val minPower = filters?.getSliderValue("min_power")
        val minConnectors = filters?.getSliderValue("min_connectors")

        val connectorsVal = filters?.getMultipleChoiceValue("connectors")
        if (connectorsVal != null && connectorsVal.values.isEmpty() && !connectorsVal.all) {
            // no connectors chosen
            return Resource.success(ChargepointList.empty())
        }
        if (connectorsVal != null && connectorsVal.values.contains("CCS")) {
            // see note about Tesla Supercharger CCS filter in getFilters below
            connectorsVal.values.add("Tesla Supercharger CCS")
        }
        val connectors = formatMultipleChoice(connectorsVal)

        val chargeCardsVal = filters?.getMultipleChoiceValue("chargecards")
        if (chargeCardsVal != null && chargeCardsVal.values.isEmpty() && !chargeCardsVal.all) {
            // no chargeCards chosen
            return Resource.success(ChargepointList.empty())
        }
        val chargeCards = formatMultipleChoice(chargeCardsVal)

        val networksVal = filters?.getMultipleChoiceValue("networks")
        if (networksVal != null && networksVal.values.isEmpty() && !networksVal.all) {
            // no networks chosen
            return Resource.success(ChargepointList.empty())
        }
        val networks = formatMultipleChoice(networksVal)

        val categoriesVal = filters?.getMultipleChoiceValue("categories")
        if (categoriesVal != null && categoriesVal.values.isEmpty() && !categoriesVal.all) {
            // no categories chosen
            return Resource.success(ChargepointList.empty())
        }
        val categories = formatMultipleChoice(categoriesVal)

        // do not use clustering if filters need to be applied locally.
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
                if (!response.isSuccessful || response.body()!!.status != STATUS_OK) {
                    return Resource.error(response.message(), null)
                } else {
                    val body = response.body()!!
                    data.addAll(body.chargelocations!!)
                    startkey = body.startkey
                }
            } catch (e: IOException) {
                return Resource.error(e.message, null)
            } catch (e: HttpException) {
                return Resource.error(e.message, null)
            }
        } while (startkey != null && startkey < 10000)

        val result = postprocessResult(data, filters)
        return Resource.success(ChargepointList(result, startkey == null))
    }

    private fun postprocessResult(
        chargers: List<GEChargepointListItem>,
        filters: FilterValues?
    ): List<ChargepointListItem> {
        val minPower = filters?.getSliderValue("min_power")
        val minConnectors = filters?.getSliderValue("min_connectors")
        val connectorsVal = filters?.getMultipleChoiceValue("connectors")
        val freecharging = filters?.getBooleanValue("freecharging")
        val freeparking = filters?.getBooleanValue("freeparking")
        val open247 = filters?.getBooleanValue("open_247")
        val barrierfree = filters?.getBooleanValue("barrierfree")
        val networks = filters?.getMultipleChoiceValue("networks")
        val chargecards = filters?.getMultipleChoiceValue("chargecards")

        return chargers.filter { it ->
            // apply filters which GoingElectric does not support natively
            if (it is GEChargeLocation) {
                it.chargepoints
                    .filter { it.power >= (minPower ?: 0) }
                    .filter { if (connectorsVal != null && !connectorsVal.all) it.type in connectorsVal.values else true }
                    .sumOf { it.count } >= (minConnectors ?: 0)
            } else {
                true
            }
        }.map {
            // infer some properties based on applied filters
            if (it is GEChargeLocation) {
                var inferred = it
                if (freecharging == true) {
                    inferred = inferred.copy(
                        cost = inferred.cost?.copy(freecharging = true)
                            ?: GECost(freecharging = true)
                    )
                }
                if (freeparking == true) {
                    inferred = inferred.copy(
                        cost = inferred.cost?.copy(freeparking = true) ?: GECost(freeparking = true)
                    )
                }
                if (open247 == true) {
                    inferred = inferred.copy(
                        openinghours = inferred.openinghours?.copy(twentyfourSeven = true)
                            ?: GEOpeningHours(twentyfourSeven = true)
                    )
                }
                if (barrierfree == true
                    && (networks == null || networks.all || it.network !in networks.values)
                    && (chargecards == null || chargecards.all)
                ) {
                    /* barrierfree, networks and chargecards are combined with OR - so we can only
                    * be sure that the charger is barrierFree if the other filters are not active
                    * or the charger does not match the other filters */
                    inferred = inferred.copy(barrierFree = true)
                }
                inferred
            } else {
                it
            }
        }.map { it.convert(apikey, false) }
    }

    override suspend fun getChargepointDetail(
        referenceData: ReferenceData,
        id: Long
    ): Resource<ChargeLocation> {
        try {
            val response = api.getChargepointDetail(id)
            return if (response.isSuccessful && response.body()!!.status == STATUS_OK && response.body()!!.chargelocations!!.size == 1) {
                Resource.success(
                    (response.body()!!.chargelocations!![0] as GEChargeLocation).convert(
                        apikey, true
                    )
                )
            } else {
                Resource.error(response.message(), null)
            }
        } catch (e: IOException) {
            return Resource.error(e.message, null)
        } catch (e: HttpException) {
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

                    if (responses.map { it.isSuccessful }.all { it }
                        && plugsResponse.body()!!.status == STATUS_OK
                        && chargeCardsResponse.body()!!.status == STATUS_OK
                        && networksResponse.body()!!.status == STATUS_OK) {
                        Resource.success(
                            GEReferenceData(
                                plugsResponse.body()!!.result!!,
                                networksResponse.body()!!.result!!,
                                chargeCardsResponse.body()!!.result!!
                            )
                        )
                    } else {
                        Resource.error(responses.find { !it.isSuccessful }?.message(), null)
                    }
                } catch (e: IOException) {
                    Resource.error(e.message, null)
                } catch (e: HttpException) {
                    Resource.error(e.message, null)
                }
            }
        }

    override fun getFilters(
        referenceData: ReferenceData,
        sp: StringProvider
    ): List<Filter<FilterValue>> {
        val refData = referenceData as GEReferenceData
        val plugs = refData.plugs
        val networks = refData.networks
        val chargeCards = refData.chargecards

        /*
        "Tesla Supercharger CCS" is a bit peculiar - it is available as a filter, but the API
        just returns "CCS" in the charging station details. So we cannot use it for filtering as
        it won't work in the local database. So we join them into a single filter option.
        If you want to find Tesla Superchargers with CCS, you can still do that using the network
        filter.
         */
        val plugMap = plugs
            .filter { it != "Tesla Supercharger CCS" }
            .associateWith { plug ->
                nameForPlugType(sp, GEChargepoint.convertTypeFromGE(plug))
            }
        val networkMap = networks.associateWith { it }
        val chargecardMap = chargeCards.associate { it.id.toString() to it.name }
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
            BooleanFilter(sp.getString(R.string.filter_exclude_faults), "exclude_faults"),
            BooleanFilter(sp.getString(R.string.filter_barrierfree), "barrierfree"),
            MultipleChoiceFilter(
                sp.getString(R.string.filter_chargecards), "chargecards",
                chargecardMap, manyChoices = true
            ),
            MultipleChoiceFilter(
                sp.getString(R.string.categories), "categories",
                categoryMap,
                manyChoices = true
            )
        )
    }

    override fun convertFiltersToSQL(
        filters: FilterValues,
        referenceData: ReferenceData
    ): FiltersSQLQuery {
        if (filters.isEmpty()) return FiltersSQLQuery("", false, false)
        var requiresChargepointQuery = false
        var requiresChargeCardQuery = false

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
                        GEChargepoint.convertTypeFromGE(
                            it
                        )
                    )
                }
            }
            result.append(" AND json_extract(cp.value, '$.type') IN (${connectorsList})")
            requiresChargepointQuery = true
        }

        // networks, chargecards and barrierFree filters are combined with OR in the GE API
        val networks = filters.getMultipleChoiceValue("networks")
        val chargecards = filters.getMultipleChoiceValue("chargecards")
        val barrierFree = filters.getBooleanValue("barrierfree")

        if ((networks != null && !networks.all) || barrierFree == true || (chargecards != null && !chargecards.all)) {
            val queries = mutableListOf<String>()
            if (networks != null && !networks.all) {
                val networksList = if (networks.values.size == 0) {
                    ""
                } else {
                    networks.values.joinToString(",") { DatabaseUtils.sqlEscapeString(it) }
                }
                queries.add("network IN (${networksList})")
            }
            if (barrierFree == true) {
                queries.add("barrierFree IS 1")
            }
            if (chargecards != null && !chargecards.all) {
                val chargecardsList = if (chargecards.values.size == 0) {
                    ""
                } else {
                    chargecards.values.joinToString(",")
                }
                queries.add("json_extract(cc.value, '$.id') IN (${chargecardsList})")
                requiresChargeCardQuery = true
            }
            result.append(" AND (${queries.joinToString(" OR ")})")
        }

        val categories = filters.getMultipleChoiceValue("categories")
        if (categories != null && !categories.all) {
            throw NotImplementedError()  // category cannot be determined in SQL
        }


        val minConnectors = filters.getSliderValue("min_connectors")
        if (minConnectors != null && minConnectors > 1) {
            result.append(" GROUP BY ChargeLocation.id HAVING SUM(json_extract(cp.value, '$.count')) >= ${minConnectors}")
            requiresChargepointQuery = true
        }

        return FiltersSQLQuery(result.toString(), requiresChargepointQuery, requiresChargeCardQuery)
    }

    override fun filteringInSQLRequiresDetails(filters: FilterValues): Boolean {
        val chargecards = filters.getMultipleChoiceValue("chargecards")
        return filters.getBooleanValue("freecharging") == true
                || filters.getBooleanValue("freeparking") == true
                || filters.getBooleanValue("open_247") == true
                || filters.getBooleanValue("barrierfree") == true
                || (chargecards != null && !chargecards.all)
    }
}

