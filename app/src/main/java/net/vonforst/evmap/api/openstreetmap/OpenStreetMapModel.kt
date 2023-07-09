package net.vonforst.evmap.api.openstreetmap

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import net.vonforst.evmap.model.*
import okhttp3.internal.immutableListOf
import java.time.Instant
import java.time.ZonedDateTime

private data class OsmSocket(
    // The OSM socket name (e.g. "type2_combo")
    val osmSocketName: String,
    // The socket identifier used in EVMap.
    // TODO: This should probably be a separate enum-like type, not a string.
    val evmapKey: String?,
) {
    /**
     * Return the OSM socket base tag (e.g. "socket:type2_combo").
     */
    fun osmSocketBaseTag(): String {
        return "socket:${this.osmSocketName}"
    }
}

// List of all OSM socket types that are relevant for EVs:
// https://wiki.openstreetmap.org/wiki/Key:socket
private val SOCKET_TYPES = immutableListOf(
    // Type 1
    OsmSocket("type1", Chargepoint.TYPE_1),
    OsmSocket("type1_combo", Chargepoint.CCS_TYPE_1),

    // Type 2
    OsmSocket("type2", Chargepoint.TYPE_2_SOCKET), // Type2 socket (or unknown)
    OsmSocket("type2_cable", Chargepoint.TYPE_2_PLUG), // Type2 plug
    OsmSocket("type2_combo", Chargepoint.CCS_TYPE_2), // CCS

    // CHAdeMO
    OsmSocket("chademo", Chargepoint.CHADEMO),

    // Tesla
    OsmSocket("tesla_standard", null),
    OsmSocket("tesla_supercharger", Chargepoint.SUPERCHARGER),

    // CEE
    OsmSocket("cee_blue", Chargepoint.CEE_BLAU), // Also known as "caravan socket"
    OsmSocket("cee_red_16a", Chargepoint.CEE_ROT),
    OsmSocket("cee_red_32a", Chargepoint.CEE_ROT),
    OsmSocket("cee_red_63a", Chargepoint.CEE_ROT),
    OsmSocket("cee_red_125a", Chargepoint.CEE_ROT),

    // Europe
    OsmSocket("schuko", Chargepoint.SCHUKO),

    // Switzerland
    OsmSocket("sev1011_t13", null),
    OsmSocket("sev1011_t15", null),
    OsmSocket("sev1011_t23", null),
    OsmSocket("sev1011_t25", null),
)

@JsonClass(generateAdapter = true)
data class OSMDocument(
    val timestamp: Instant,
    val elements: List<OSMChargingStation>
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
        null, // TODO: Can we determine this with overpass?
        getChargepoints(),
        tags["network"],
        "https://www.openstreetmap.org/node/$id",
        "https://www.openstreetmap.org/edit?node=$id",
        null,
        false, // We don't know
        tags["authentication:none"] == "yes",
        tags["operator"],
        tags["description"],
        null,
        null,
        null,
        null,
        getOpeningHours(),
        getCost(),
        "© OpenStreetMap contributors",
        null,
        null,
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
            ?: "Charging Station"
    }

    /**
     * Return the chargepoints for this charging station.
     */
    private fun getChargepoints(): List<Chargepoint> {
        // Note: In OSM, the chargepoints are mapped as "socket:<type> = <count>"
        val chargepoints = mutableListOf<Chargepoint>()
        for (socket in SOCKET_TYPES) {
            val count = try {
                (this.tags[socket.osmSocketBaseTag()] ?: "0").toInt()
            } catch (e: NumberFormatException) {
                0
            }
            if (count > 0) {
                if (socket.evmapKey != null) {
                    val outputPower = parseOutputPower(this.tags["${socket.osmSocketBaseTag()}:output"])
                    chargepoints.add(Chargepoint(socket.evmapKey, outputPower, count))
                }
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

    private fun getCost(): Cost {
        val freecharging = when (tags["fee"]?.lowercase()) {
            "yes", "y" -> false
            "no", "n" -> true
            else -> null
        }
        val freeparking = when (tags["parking:fee"]?.lowercase()) {
            "no", "n" -> true
            "yes", "y", "interval" -> false
            else -> null
        }
        val description = listOfNotNull(tags["charge"], tags["charge:conditional"]).ifEmpty { null }
            ?.joinToString("\n")
        return Cost(freecharging, freeparking, null, description)
    }

    companion object {
        /**
         * Parse raw OSM output power.
         *
         * The proper format to map output power for an EV charging station is "<amount> kW",
         * for example "22 kW" or "3.7 kW". Some fields in the wild are tagged with the unit "kVA"
         * instead of "kW", those can be treated as equivalent.
         *
         * Sometimes people also mapped plain numbers (e.g. 7000, I assume that's 7 kW),
         * ranges (5,5 - 11 kW, huh?) or even current (32 A), which is wrong. If we cannot parse,
         * just ignore the field.
         */
        fun parseOutputPower(rawOutput: String?): Double? {
            if (rawOutput == null) {
                return null
            }
            val pattern = Regex("([0-9.,]+)\\s*(kW|kVA)", setOf(RegexOption.IGNORE_CASE))
            val matchResult = pattern.matchEntire(rawOutput) ?: return null
            val numberString = matchResult.groupValues[1].replace(',', '.')
            return numberString.toDoubleOrNull()
        }
    }
}