package net.vonforst.evmap.api.fronyx

import com.squareup.moshi.JsonClass
import java.time.ZonedDateTime

@JsonClass(generateAdapter = true)
data class FronyxEvseIdResponse(
    val evseId: String,
    val predictions: List<FronyxPrediction>
)

@JsonClass(generateAdapter = true)
data class FronyxPrediction(
    val timestamp: ZonedDateTime,
    val status: FronyxStatus
)

enum class FronyxStatus {
    AVAILABLE, UNAVAILABLE
}