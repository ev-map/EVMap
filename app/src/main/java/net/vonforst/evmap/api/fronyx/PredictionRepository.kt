package net.vonforst.evmap.api.fronyx

import android.content.Context
import net.vonforst.evmap.R
import net.vonforst.evmap.api.availability.ChargeLocationStatus
import net.vonforst.evmap.api.equivalentPlugTypes
import net.vonforst.evmap.api.nameForPlugType
import net.vonforst.evmap.api.stringProvider
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.Chargepoint
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.viewmodel.Resource
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class PredictionData(
    val predictionGraph: Map<ZonedDateTime, Double>?,
    val maxValue: Double,
    val predictedChargepoints: List<Chargepoint>,
    val isPercentage: Boolean,
    val description: String?
)

class PredictionRepository(private val context: Context) {
    private val predictionApi = FronyxApi(context.getString(R.string.fronyx_key))
    private val prefs = PreferenceDataSource(context)

    suspend fun getPredictionData(
        charger: ChargeLocation,
        availability: ChargeLocationStatus?,
        filteredConnectors: Set<String>? = null
    ): PredictionData {
        val fronyxPrediction = availability?.evseIds?.let { evseIds ->
            getFronyxPrediction(charger, evseIds, filteredConnectors)
        }?.data
        val graph = buildPredictionGraph(availability, fronyxPrediction)
        val predictedChargepoints = getPredictedChargepoints(charger, filteredConnectors)
        val maxValue = getPredictionMaxValue(availability, fronyxPrediction, predictedChargepoints)
        val isPercentage = predictionIsPercentage(availability, fronyxPrediction)
        val description = getDescription(charger, predictedChargepoints)
        return PredictionData(
            graph, maxValue, predictedChargepoints, isPercentage, description
        )
    }

    private suspend fun getFronyxPrediction(
        charger: ChargeLocation,
        evseIds: Map<Chargepoint, List<String>>,
        filteredConnectors: Set<String>?
    ): Resource<List<FronyxEvseIdResponse>> {
        return Resource.success(null)

        /*val allEvseIds =
            evseIds.filterKeys {
                FronyxApi.isChargepointSupported(charger, it) &&
                        filteredConnectors?.let { filtered ->
                            equivalentPlugTypes(
                                it.type
                            ).any { filtered.contains(it) }
                        } ?: true
            }.flatMap { it.value }
        if (allEvseIds.isEmpty()) {
            return Resource.success(emptyList())
        }
        try {
            val result = predictionApi.getPredictionsForEvseIds(allEvseIds)
            if (result.size == allEvseIds.size) {
                return Resource.success(result)
            } else {
                return Resource.error("not all EVSEIDs found", null)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return Resource.error(e.message, null)
        } catch (e: HttpException) {
            e.printStackTrace()
            return Resource.error(e.message, null)
        } catch (e: AvailabilityDetectorException) {
            e.printStackTrace()
            return Resource.error(e.message, null)
        } catch (e: JsonDataException) {
            // malformed JSON response from fronyx API
            e.printStackTrace()
            return Resource.error(e.message, null)
        }*/
    }

    private fun buildPredictionGraph(
        availability: ChargeLocationStatus?,
        prediction: List<FronyxEvseIdResponse>?
    ): Map<ZonedDateTime, Double>? {
        val congestionHistogram = availability?.congestionHistogram
        return if (congestionHistogram != null && prediction == null) {
            congestionHistogram.mapIndexed { i, value ->
                LocalTime.of(i, 0).atDate(LocalDate.now())
                    .atZone(ZoneId.systemDefault()) to value
            }.toMap()
        } else {
            prediction?.let { responses ->
                if (responses.isEmpty()) {
                    null
                } else {
                    val evseIds = responses.map { it.evseId }
                    val groupByTimestamp = responses.flatMap { response ->
                        response.predictions.map {
                            Triple(
                                it.timestamp,
                                response.evseId,
                                it.status
                            )
                        }
                    }
                        .groupBy { it.first }  // group by timestamp
                        .mapValues { it.value.map { it.second to it.third } }  // only keep EVSEID and status
                        .filterValues { it.map { it.first } == evseIds }  // remove values where status is not given for all EVSEs
                        .filterKeys { it > ZonedDateTime.now() }  // only show predictions in the future

                    groupByTimestamp.mapValues {
                        it.value.count {
                            it.second == FronyxStatus.UNAVAILABLE
                        }.toDouble()
                    }.ifEmpty { null }
                }
            }
        }
    }

    private fun getPredictedChargepoints(
        charger: ChargeLocation,
        filteredConnectors: Set<String>?
    ) =
        charger.chargepoints.filter {
            FronyxApi.isChargepointSupported(charger, it) &&
                    filteredConnectors?.let { filtered ->
                        equivalentPlugTypes(it.type).any {
                            filtered.contains(
                                it
                            )
                        }
                    } ?: true
        }

    private fun getPredictionMaxValue(
        availability: ChargeLocationStatus?,
        prediction: List<FronyxEvseIdResponse>?,
        predictedChargepoints: List<Chargepoint>
    ): Double = if (availability?.congestionHistogram != null && prediction == null) {
        1.0
    } else {
        predictedChargepoints.sumOf { it.count }.toDouble()
    }

    private fun predictionIsPercentage(
        availability: ChargeLocationStatus?,
        prediction: List<FronyxEvseIdResponse>?
    ) =
        availability?.congestionHistogram != null && prediction == null


    private fun getDescription(
        charger: ChargeLocation,
        predictedChargepoints: List<Chargepoint>
    ): String? {
        val allChargepoints = charger.chargepoints

        val predictedChargepointTypes = predictedChargepoints.map { it.type }.distinct()
        return if (allChargepoints == predictedChargepoints) {
            null
        } else if (predictedChargepointTypes.size == 1) {
            context.getString(
                R.string.prediction_only,
                nameForPlugType(context.stringProvider(), predictedChargepointTypes[0])
            )
        } else {
            context.getString(
                R.string.prediction_only,
                context.getString(R.string.prediction_dc_plugs_only)
            )
        }
    }
}