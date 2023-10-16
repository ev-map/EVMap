package net.vonforst.evmap.api.availability

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.vonforst.evmap.R
import net.vonforst.evmap.addDebugInterceptors
import net.vonforst.evmap.api.RateLimitInterceptor
import net.vonforst.evmap.api.await
import net.vonforst.evmap.api.equivalentPlugTypes
import net.vonforst.evmap.cartesianProduct
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.storage.EncryptedPreferenceDataStore
import net.vonforst.evmap.viewmodel.Resource
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

interface AvailabilityDetector {
    suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus

    /**
     * Get a rough estimate whether this charger is supported by this provider.
     *
     * This might be done by checking supported countries, or even by matching the operator
     * for operator-specific availability detectors.
     */
    fun isChargerSupported(charger: ChargeLocation): Boolean
}

abstract class BaseAvailabilityDetector(private val client: OkHttpClient) : AvailabilityDetector {
    protected val radius = 150  // max radius in meters

    protected suspend fun httpGet(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) throw IOException(response.message)

        val str = response.body!!.string()
        return str
    }

    protected fun getCorrespondingChargepoint(
        cps: Iterable<Chargepoint>, type: String, power: Double
    ): Chargepoint? {
        var filter = cps.filter {
            it.type == type
        }
        if (filter.size > 1) {
            filter = filter.filter {
                if (power > 0) {
                    it.power == power
                } else true
            }
            // TODO: handle not matching powers
            /*if (filter.isEmpty()) {
                filter = listOfNotNull(cps.minBy {
                    abs(it.power - power)
                })
            }*/
        }
        return filter.getOrNull(0)
    }

    companion object {
        internal fun matchChargepoints(
            connectors: Map<Long, Pair<Double, String>>,
            chargepoints: List<Chargepoint>
        ): Map<Chargepoint, Set<Long>> {
            var cpts = chargepoints

            // iterate over each connector type
            val types = connectors.map { it.value.second }.distinct().toSet()
            val equivalentTypes = types.map { equivalentPlugTypes(it).plus(it) }.cartesianProduct()
            var geTypes = cpts.map { it.type }.distinct().toSet()
            if (!equivalentTypes.any { it == geTypes } && geTypes.size > 1 && geTypes.contains(
                    Chargepoint.SCHUKO
                )) {
                // If charger has household plugs and other plugs, try removing the household plugs
                // (common e.g. in Hamburg -> 2x Type 2 + 2x Schuko, but NM only lists Type 2)
                geTypes = geTypes.filter { it != Chargepoint.SCHUKO }.toSet()
                cpts = cpts.filter { it.type != Chargepoint.SCHUKO }
            }
            if (!equivalentTypes.any { it == geTypes }) throw AvailabilityDetectorException("chargepoints do not match")
            return types.flatMap { type ->
                // find connectors of this type
                val connsOfType = connectors.filter { it.value.second == type }
                // find powers this connector is available as
                val powers = connsOfType.map { it.value.first }.distinct().sorted()
                // find corresponding powers in GE data
                val gePowers =
                    cpts.filter { equivalentPlugTypes(it.type).any { it == type } }
                        .mapNotNull { it.power }.distinct().sorted()

                // if the distinct number of powers is the same, try to match.
                if (powers.size == gePowers.size) {
                    gePowers.zip(powers).map { (gePower, power) ->
                        val chargepoint =
                            cpts.find { equivalentPlugTypes(it.type).any { it == type } && it.power == gePower }!!
                        val ids = connsOfType.filter { it.value.first == power }.keys
                        if (chargepoint.count != ids.size) {
                            throw AvailabilityDetectorException("chargepoints do not match")
                        }
                        chargepoint to ids
                    }
                } else if (powers.size == 1 && gePowers.size == 2
                    && cpts.sumOf { it.count } == connsOfType.size
                ) {
                    // special case: dual charger(s) with load balancing
                    // GoingElectric shows 2 different powers, NewMotion just one
                    val allIds = connsOfType.keys.toList()
                    var i = 0
                    gePowers.map { gePower ->
                        val chargepoint =
                            cpts.find { it.type in equivalentPlugTypes(type) && it.power == gePower }!!
                        val ids = allIds.subList(i, i + chargepoint.count).toSet()
                        i += chargepoint.count
                        chargepoint to ids
                    }
                    // TODO: this will not necessarily first fill up the higher-power chargepoint
                } else {
                    throw AvailabilityDetectorException("chargepoints do not match")
                }
            }.toMap()
        }
    }
}

data class ChargeLocationStatus(
    val status: Map<Chargepoint, List<ChargepointStatus>>,
    val source: String,
    val evseIds: Map<Chargepoint, List<String>>? = null,
    val congestionHistogram: List<Double>? = null,
    val extraData: Any? = null  // API-specific data
) {
    fun applyFilters(connectors: Set<String>?, minPower: Int?): ChargeLocationStatus {
        val statusFiltered = status.filterKeys {
            (connectors == null || connectors.map {
                equivalentPlugTypes(it)
            }.any { equivalent -> it.type in equivalent })
                    && (minPower == null || (it.power != null && it.power >= minPower))
        }
        return this.copy(status = statusFiltered)
    }

    val totalChargepoints = status.map { it.key.count }.sum()
}

enum class ChargepointStatus {
    AVAILABLE, UNKNOWN, CHARGING, OCCUPIED, FAULTED
}

class AvailabilityDetectorException(message: String) : Exception(message)

private val cookieManager = CookieManager().apply {
    setCookiePolicy(CookiePolicy.ACCEPT_ALL)
}

class AvailabilityRepository(context: Context) {
    private val okhttp = OkHttpClient.Builder()
        .addInterceptor(RateLimitInterceptor())
        .addDebugInterceptors()
        .readTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .cookieJar(JavaNetCookieJar(cookieManager))
        .build()
    private val teslaAvailabilityDetector = run {
        val (clientId, clientSecret) = context.getString(R.string.tesla_credentials).split(":")
        TeslaAvailabilityDetector(
            okhttp,
            EncryptedPreferenceDataStore(context),
            clientId,
            clientSecret
        )
    }
    private val availabilityDetectors = listOf(
        RheinenergieAvailabilityDetector(okhttp),
        teslaAvailabilityDetector,
        EnBwAvailabilityDetector(okhttp),
        NewMotionAvailabilityDetector(okhttp)
    )

    suspend fun getAvailability(charger: ChargeLocation): Resource<ChargeLocationStatus> {
        var value: Resource<ChargeLocationStatus>? = null
        withContext(Dispatchers.IO) {
            for (ad in availabilityDetectors) {
                if (!ad.isChargerSupported(charger)) continue
                try {
                    value = Resource.success(ad.getAvailability(charger))
                    break
                } catch (e: IOException) {
                    value = Resource.error(e.message, null)
                    e.printStackTrace()
                } catch (e: HttpException) {
                    value = Resource.error(e.message, null)
                    e.printStackTrace()
                } catch (e: AvailabilityDetectorException) {
                    value = Resource.error(e.message, null)
                    e.printStackTrace()
                }
            }
        }
        return value ?: Resource.error(null, null)
    }

    fun isSupercharger(charger: ChargeLocation) =
        teslaAvailabilityDetector.isChargerSupported(charger)

    fun isTeslaSupported(charger: ChargeLocation) =
        teslaAvailabilityDetector.isChargerSupported(charger) && teslaAvailabilityDetector.isSignedIn()
}
