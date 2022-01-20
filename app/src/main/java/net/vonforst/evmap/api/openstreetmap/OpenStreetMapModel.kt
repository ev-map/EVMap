package net.vonforst.evmap.api.openstreetmap

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import net.vonforst.evmap.model.*
import java.time.Instant
import java.time.ZonedDateTime

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
        // TODO
        return emptyList()
    }

    private fun getOpeningHours(): OpeningHours? {
        if (tags["opening_hours"] == "24/7") {
            return OpeningHours(true, null, null)
        }
        // TODO: Try to convert other formats as well
        return null
    }
}