package net.vonforst.evmap.api.availability

import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.vonforst.evmap.api.availability.tesla.ChargerAvailability
import net.vonforst.evmap.api.availability.tesla.TeslaChargingGuestGraphQlApi
import net.vonforst.evmap.api.availability.tesla.TeslaCuaApi
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.utils.distanceBetween
import okhttp3.OkHttpClient

private const val coordRange = 0.005  // range of latitude and longitude for loading the map

class TeslaGuestAvailabilityDetector(
    client: OkHttpClient,
    baseUrl: String? = null
) :
    BaseAvailabilityDetector(client) {

    private var cuaApi = TeslaCuaApi.create(client, baseUrl)
    private var api = TeslaChargingGuestGraphQlApi.create(client, baseUrl)

    override suspend fun getAvailability(location: ChargeLocation): ChargeLocationStatus {
        val results = cuaApi.getTeslaLocations()

        val result =
            results.minByOrNull {
                if (it.latitude != null && it.longitude != null) {
                    distanceBetween(
                        it.latitude,
                        it.longitude,
                        location.coordinates.lat,
                        location.coordinates.lng
                    )
                } else Double.POSITIVE_INFINITY
            } ?: throw AvailabilityDetectorException("no candidates found.")

        val resultDetails = try {
            cuaApi.getTeslaLocation(result.locationId)
        } catch (e: JsonDataException) {
            // instead of a single location, this may also return an empty JSON list []. This is hard to fix with Moshi
            if (e.message == "Expected BEGIN_OBJECT but was BEGIN_ARRAY at path \$") {
                throw AvailabilityDetectorException("no candidates found.")
            } else {
                throw e
            }
        }
        val trtId = resultDetails.trtId?.toLongOrNull()
            ?: throw AvailabilityDetectorException("charger data not available through guest API")

        val (detailsA, guestPricing) = coroutineScope {
            val details = async {
                api.getSiteDetails(
                    TeslaChargingGuestGraphQlApi.GetSiteDetailsRequest(
                        TeslaChargingGuestGraphQlApi.GetSiteDetailsVariables(
                            TeslaChargingGuestGraphQlApi.Identifier(
                                TeslaChargingGuestGraphQlApi.ChargingSiteIdentifier(
                                    trtId, TeslaChargingGuestGraphQlApi.Experience.ADHOC
                                )
                            ),
                        )
                    )
                ).data.chargingNetwork?.site
                    ?: throw AvailabilityDetectorException("no candidates found.")
            }
            val guestPricing = async {
                api.getSiteDetails(
                    TeslaChargingGuestGraphQlApi.GetSiteDetailsRequest(
                        TeslaChargingGuestGraphQlApi.GetSiteDetailsVariables(
                            TeslaChargingGuestGraphQlApi.Identifier(
                                TeslaChargingGuestGraphQlApi.ChargingSiteIdentifier(
                                    trtId, TeslaChargingGuestGraphQlApi.Experience.GUEST
                                )
                            ),
                        )
                    )
                ).data.chargingNetwork?.site?.pricing
            }
            details to guestPricing
        }
        val details = detailsA.await()

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

        var detailsSorted = details.chargerList
            .sortedBy { c -> c.labelLetter }
            .sortedBy { c -> c.labelNumber }

        if (detailsSorted.size != scV2Connectors.sumOf { it.count } + scV3Connectors.sumOf { it.count }) {
            // apparently some connectors are missing in Tesla data
            // If we have just one type of charger, we can still match
            val numMissing =
                scV2Connectors.sumOf { it.count } + scV3Connectors.sumOf { it.count } - detailsSorted.size
            if ((scV2Connectors.isEmpty() || scV3Connectors.isEmpty()) && numMissing > 0) {
                detailsSorted =
                    detailsSorted + List(numMissing) {
                        TeslaChargingGuestGraphQlApi.ChargerDetail(
                            ChargerAvailability.UNKNOWN,
                            "", ""
                        )
                    }
            } else {
                throw AvailabilityDetectorException("Tesla API chargepoints do not match data source")
            }
        }

        val detailsMap =
            mutableMapOf<Chargepoint, List<TeslaChargingGuestGraphQlApi.ChargerDetail>>()
        var i = 0
        for (connector in scV2Connectors) {
            detailsMap[connector] =
                detailsSorted.subList(i, i + connector.count)
            i += connector.count
        }
        if (scV2CCSConnectors.isNotEmpty()) {
            i = 0
            for (connector in scV2CCSConnectors) {
                detailsMap[connector] =
                    detailsSorted.subList(i, i + connector.count)
                i += connector.count
            }
        }
        for (connector in scV3Connectors) {
            detailsMap[connector] =
                detailsSorted.subList(i, i + connector.count)
            i += connector.count
        }

        val statusMap = detailsMap.mapValues { it.value.map { it.availability.toStatus() } }
        val labelsMap = detailsMap.mapValues { it.value.map { it.label } }

        val pricing = details.pricing.copy(memberRates = guestPricing.await()?.userRates)

        return ChargeLocationStatus(
            statusMap,
            "Tesla",
            labels = labelsMap,
            extraData = pricing
        )
    }

    override fun isChargerSupported(charger: ChargeLocation): Boolean {
        return when (charger.dataSource) {
            "goingelectric" -> charger.network == "Tesla Supercharger"
            "openchargemap" -> charger.chargepriceData?.network in listOf("23", "3534")
            else -> false
        }
    }
}