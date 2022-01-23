package net.vonforst.evmap.api.openstreetmap

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import net.vonforst.evmap.model.*
import okhttp3.internal.immutableListOf
import java.time.Instant
import java.time.ZonedDateTime

// List of all OSM socket types that are relevant for EVs:
// https://wiki.openstreetmap.org/wiki/Key:socket
val SOCKET_TYPES = immutableListOf(
    // Type 1
    "type1",
    "type1_combo",

    // Type 2
    "type2", // Type2 socket
    "type2_cable", // Type2 with a fixed attached cable
    "type2_combo", // CCS

    // CHAdeMO
    "chademo",

    // Tesla
    "tesla_standard",
    "tesla_supercharger",

    // CEE
    "cee_blue", // Also known as "caravan socket"
    "cee_red_16a",
    "cee_red_32a",
    "cee_red_63a",
    "cee_red_125a",

    // Switzerland
    "sev1011_t13",
    "sev1011_t15",
    "sev1011_t23",
    "sev1011_t25",
)

@JsonClass(generateAdapter = true)
data class OSMChargingStation(
    // Unique numeric ID
    val id: Long,
    // Latitude (WGS84)
    val lat: Double,
    // Longitude (WGS84)
    val lon: Double,
    // Timestamp of last update
    @Json(name = "timestamp") val lastUpdateTimestamp: ZonedDateTime,
    // Numeric, monotonically increasing version number
    val version: Int,
    // User that last modified this POI
    val user: String,
    // Raw key-value OSM tags
    val tags: Map<String, String>,
) {
    /**
     * Convert the [OSMChargingStation] to a generic [ChargeLocation].
     *
     * The [dataFetchTimestamp] should be set to the timestamp when the data was last
     * refreshed / fetched from OSM. It will always be later than the [lastUpdateTimestamp],
     * which contains the timestamp when the data was last _edited_ in OSM.
     */
    fun convert(dataFetchTimestamp: Instant) = ChargeLocation(
        id,
        "openstreetmap",
        getName(),
        Coordinate(lat, lon),
        Address("", "", "", ""), // TODO: Can we determine this with overpass?
        getChargepoints(),
        tags["network"],
        "https://www.openstreetmap.org/node/$id",
        "https://www.openstreetmap.org/edit?node=$id",
        null,
        false, // We don't know
        null, // What does this entail?
        tags["operator"],
        tags["description"],
        null,
        null,
        null,
        null,
        getOpeningHours(),
        null,
        "© OpenStreetMap contributors",
        null,
        dataFetchTimestamp,
        true,
    )

    /**
     * Return the name for this charging station.
     */
    private fun getName(): String {
        // Ideally this station has a name.
        // If not, fall back to the operator.
        // If that is missing as well, use a generic "Charging Station" string.
        return tags["name"]
            ?: tags["operator"]
            ?: "Charging Station";
    }

    /**
     * Return the chargepoints for this charging station.
     */
    private fun getChargepoints(): List<Chargepoint> {
        // Note: In OSM, the chargepoints are mapped as "socket:<type> = <count>"
        val chargepoints = mutableListOf<Chargepoint>()
        for (socket in SOCKET_TYPES) {
            val count = try {
                (this.tags["socket:$socket"] ?: "0").toInt()
            } catch (e: NumberFormatException) {
                0
            }
            if (count > 0) {
                chargepoints.add(Chargepoint(socket, 42.0, count))
                // TODO: Power parsing
            }
        }
        return chargepoints
    }

    private fun getOpeningHours(): OpeningHours? {
        val rawOpeningHours = tags["opening_hours"] ?: return null

        // Handle the simple 24/7 case
        if (rawOpeningHours == "24/7") {
            return OpeningHours(true, null, null)
        }

        // TODO: Try to convert other formats as well?
        //
        // Note: The current {@link OpeningHours} format is not flexible enough to handle
        // all rules that OSM can represent and might need to be updated.
        // This library could help: https://github.com/simonpoole/OpeningHoursParser
        //
        // Alternatively, with the opening-hours-evaluator library
        // https://github.com/leonardehrenfried/opening-hours-evaluator
        // we could implement an "open now" feature.
        return null
    }
}