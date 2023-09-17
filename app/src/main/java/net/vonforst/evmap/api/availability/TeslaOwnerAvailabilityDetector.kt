package net.vonforst.evmap.api.availability

import net.vonforst.evmap.api.availability.tesla.ChargerAvailability
import net.vonforst.evmap.api.availability.tesla.TeslaAuthenticationApi
import net.vonforst.evmap.api.availability.tesla.TeslaChargingOwnershipGraphQlApi
import net.vonforst.evmap.api.availability.tesla.asTeslaCoord
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.Collections

private const val coordRange = 0.005  // range of latitude and longitude for loading the map

class TeslaOwnerAvailabilityDetector(
    private val client: OkHttpClient,
    private val tokenStore: TokenStore,
    private val baseUrl: String? = null
) :
    BaseAvailabilityDetector(client) {

    private val authApi = TeslaAuthenticationApi.create(client, null)
    private var api: TeslaChargingOwnershipGraphQlApi? = null

    interface TokenStore {
        var teslaRefreshToken: String?
        var teslaAccessToken: String?
        var teslaAccessTokenExpiry: Long
    }

    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val api = initApi()
        val req = TeslaChargingOwnershipGraphQlApi.GetNearbyChargingSitesRequest(
            TeslaChargingOwnershipGraphQlApi.GetNearbyChargingSitesVariables(
                TeslaChargingOwnershipGraphQlApi.GetNearbyChargingSitesArgs(
                    location.coordinates.asTeslaCoord(),
                    TeslaChargingOwnershipGraphQlApi.Coordinate(
                        location.coordinates.lat + coordRange,
                        location.coordinates.lng - coordRange
                    ),
                    TeslaChargingOwnershipGraphQlApi.Coordinate(
                        location.coordinates.lat - coordRange,
                        location.coordinates.lng + coordRange
                    ),
                    TeslaChargingOwnershipGraphQlApi.OpenToNonTeslasFilterValue(false)
                )
            )
        )
        val results = api.getNearbyChargingSites(
            req,
            req.operationName
        ).data.charging?.nearbySites?.sitesAndDistances
            ?: throw AvailabilityDetectorException("no candidates found.")
        val result =
            results.minByOrNull { it.haversineDistanceMiles.value }
                ?: throw AvailabilityDetectorException("no candidates found.")

        val details = api.getChargingSiteInformation(
            TeslaChargingOwnershipGraphQlApi.GetChargingSiteInformationRequest(
                TeslaChargingOwnershipGraphQlApi.GetChargingSiteInformationVariables(
                    TeslaChargingOwnershipGraphQlApi.ChargingSiteIdentifier(result.id.text),
                    TeslaChargingOwnershipGraphQlApi.VehicleMakeType.NON_TESLA
                )
            )
        ).data.charging.site ?: throw AvailabilityDetectorException("no candidates found.")


        val scV2Connectors = location.chargepoints.filter { it.type == Chargepoint.SUPERCHARGER }
        val scV2CCSConnectors = location.chargepoints.filter {
            it.type in listOf(
                Chargepoint.CCS_TYPE_2,
                Chargepoint.CCS_UNKNOWN
            ) && it.power != null && it.power <= 150
        }
        if (scV2CCSConnectors.sumOf { it.count } != 0 && scV2CCSConnectors.sumOf { it.count } != scV2Connectors.sumOf { it.count }) {
            throw AvailabilityDetectorException("number of V2 connectors does not match number of V2 CCS connectors")
        }
        val scV3Connectors = location.chargepoints.filter {
            it.type in listOf(
                Chargepoint.CCS_TYPE_2,
                Chargepoint.CCS_UNKNOWN
            ) && it.power != null && it.power > 150
        }
        if (location.totalChargepoints != scV2Connectors.sumOf { it.count } + scV3Connectors.sumOf { it.count } + scV2CCSConnectors.sumOf { it.count }) throw AvailabilityDetectorException(
            "charger has unknown connectors"
        )

        var statusSorted = details.siteDynamic.chargerDetails
            .sortedBy { c ->
                c.charger.labelLetter
                    ?: details.siteStatic.chargers.find { it.id == c.charger.id }?.labelLetter
            }
            .sortedBy { c ->
                c.charger.labelNumber
                    ?: details.siteStatic.chargers.find { it.id == c.charger.id }?.labelNumber
            }
            .map { it.availability }
        if (statusSorted.size != scV2Connectors.sumOf { it.count } + scV3Connectors.sumOf { it.count }) {
            // apparently some connectors are missing in Tesla data
            // If we have just one type of charger, we can still match
            val numMissing =
                scV2Connectors.sumOf { it.count } + scV3Connectors.sumOf { it.count } - statusSorted.size
            if (scV2Connectors.isEmpty() || scV3Connectors.isEmpty() && numMissing > 0) {
                statusSorted =
                    statusSorted + List(numMissing) { ChargerAvailability.UNKNOWN }
            } else {
                throw AvailabilityDetectorException("Tesla API chargepoints do not match data source")
            }
        }

        val statusMap = emptyMap<Chargepoint, List<ChargepointStatus>>().toMutableMap()
        var i = 0
        for (connector in scV2Connectors) {
            statusMap[connector] =
                statusSorted.subList(i, i + connector.count).map { it.toStatus() }
            i += connector.count
        }
        if (scV2CCSConnectors.isNotEmpty()) {
            i = 0
            for (connector in scV2CCSConnectors) {
                statusMap[connector] =
                    statusSorted.subList(i, i + connector.count).map { it.toStatus() }
                i += connector.count
            }
        }
        for (connector in scV3Connectors) {
            statusMap[connector] =
                statusSorted.subList(i, i + connector.count).map { it.toStatus() }
            i += connector.count
        }

        val congestionHistogram = details.congestionPriceHistogram?.let { cph ->
            val indexOfMidnight = cph.dataAttributes.indexOfFirst { it.label == "12AM" }
            indexOfMidnight.takeIf { it >= 0 }?.let { index ->
                val data = cph.data.toMutableList()
                Collections.rotate(data, -index)
                data
            }
        }

        return ChargeLocationStatus(
            statusMap,
            "Tesla",
            congestionHistogram = congestionHistogram,
            extraData = details.pricing
        )
    }

    override fun isChargerSupported(charger: ChargeLocation): Boolean {
        return when (charger.dataSource) {
            "goingelectric" -> charger.network == "Tesla Supercharger"
            "openchargemap" -> charger.chargepriceData?.network in listOf("23", "3534")
            "openstreetmap" -> charger.operator in listOf("Tesla, Inc.", "Tesla")
            else -> false
        }
    }

    private suspend fun initApi(): TeslaChargingOwnershipGraphQlApi {

        return api ?: run {
            val newApi = TeslaChargingOwnershipGraphQlApi.create(client, baseUrl) {
                val now = Instant.now().epochSecond
                val token =
                    tokenStore.teslaAccessToken.takeIf { tokenStore.teslaAccessTokenExpiry > now }
                        ?: run {
                            val refreshToken = tokenStore.teslaRefreshToken
                                ?: throw NotSignedInException()
                            val response =
                                authApi.getToken(
                                    TeslaAuthenticationApi.RefreshTokenRequest(
                                        refreshToken
                                    )
                                )
                            tokenStore.teslaAccessToken = response.accessToken
                            tokenStore.teslaAccessTokenExpiry = now + response.expiresIn
                            response.accessToken
                        }
                token
            }
            api = newApi
            newApi
        }
    }

    fun isSignedIn() = tokenStore.teslaRefreshToken != null

}