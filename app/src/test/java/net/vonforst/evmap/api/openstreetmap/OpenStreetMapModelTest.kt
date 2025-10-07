package net.vonforst.evmap.api.openstreetmap

import com.squareup.moshi.Moshi
import net.vonforst.evmap.api.openchargemap.ZonedDateTimeAdapter
import net.vonforst.evmap.model.Chargepoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.Month
import java.time.ZoneOffset

const val JSON_SINGLE = "{\n" +
"  \"id\": 9084665785,\n" +
"  \"lat\": 46.1137872,\n" +
"  \"lon\": 7.0778715,\n" +
"  \"timestamp\": \"2021-09-12T19:36:56Z\",\n" +
"  \"version\": 1,\n" +
"  \"user\": \"Voonosm\",\n" +
"  \"tags\": {\n" +
"    \"amenity\": \"charging_station\",\n" +
"    \"authentication:app\": \"yes\",\n" +
"    \"authentication:contactless\": \"yes\",\n" +
"    \"bicycle\": \"no\",\n" +
"    \"capacity\": \"2\",\n" +
"    \"cover\": \"no\",\n" +
"    \"fee\": \"yes\",\n" +
"    \"motorcar\": \"yes\",\n" +
"    \"network\": \"Swisscharge\",\n" +
"    \"opening_hours\": \"24/7\",\n" +
"    \"operator\": \"GOFAST\",\n" +
"    \"parking:fee\": \"no\",\n" +
"    \"payment:credit_cards\": \"yes\",\n" +
"    \"socket:chademo\": \"2\",\n" +
"    \"socket:chademo:output\": \"60 kW\",\n" +
"    \"socket:type2\": \"1\",\n" +
"    \"socket:type2:output\": \"22 kW\",\n" +
"    \"socket:type2_combo\": \"2\",\n" +
"    \"socket:type2_combo:output\": \"150 kW\"\n" +
"  }\n" +
"}"

class OpenStreetMapModelTest {
    @Test
    fun parseFromJson() {
        val moshi = Moshi.Builder()
            .add(ZonedDateTimeAdapter())
            .build()
        val deserialized = moshi
            .adapter(OSMChargingStation::class.java)
            .fromJson(JSON_SINGLE)!!
        assertEquals(9084665785, deserialized.id)
        assertEquals(1, deserialized.version)
        assertEquals(12, deserialized.lastUpdateTimestamp.dayOfMonth)
        assertEquals(Month.SEPTEMBER, deserialized.lastUpdateTimestamp.month)
        assertEquals(36, deserialized.lastUpdateTimestamp.minute)
        assertEquals(ZoneOffset.UTC, deserialized.lastUpdateTimestamp.offset)
        assertEquals("Swisscharge", deserialized.tags["network"])
    }

    @Test
    fun convert() {
        val osmChargingStation = Moshi.Builder()
            .add(ZonedDateTimeAdapter())
            .build()
            .adapter(OSMChargingStation::class.java)
            .fromJson(JSON_SINGLE)!!
        val now = Instant.now()
        val chargeLocation = osmChargingStation.convert(now)

        // Basics
        assertEquals("openstreetmap", chargeLocation.dataSource)
        assertEquals("https://www.openstreetmap.org/node/9084665785", chargeLocation.url)
        assertEquals(true, chargeLocation.openinghours?.twentyfourSeven)
        assertEquals("GOFAST", chargeLocation.name) // Fallback to operator because name is not set
        assertEquals(false, chargeLocation.barrierFree) // False because `authentication:none` isn't set
        assertEquals(now, chargeLocation.timeRetrieved)

        // Cost
        assertEquals(false, chargeLocation.cost?.freecharging)
        assertEquals(true, chargeLocation.cost?.freeparking)

        // Chargepoints
        assertEquals(3, chargeLocation.chargepoints.size)
        val ccs = chargeLocation.chargepoints.single { it.type == Chargepoint.CCS_TYPE_2 }
        val type2 = chargeLocation.chargepoints.single { it.type == Chargepoint.TYPE_2_SOCKET }
        val chademo = chargeLocation.chargepoints.single { it.type == Chargepoint.CHADEMO }
        assertEquals(2, ccs.count)
        assertEquals(150.0, ccs.power)
        assertEquals(1, type2.count)
        assertEquals(22.0, type2.power)
        assertEquals(2, chademo.count)
        assertEquals(60.0, chademo.power)
    }

    @Test
    fun parseOutputPower() {
        // Null input -> null output
        assertNull(OSMChargingStation.parseOutputPower(null))

        // Invalid input -> null output
        assertNull(OSMChargingStation.parseOutputPower(""))
        assertNull(OSMChargingStation.parseOutputPower("a"))
        assertNull(OSMChargingStation.parseOutputPower("22 A"))

        // Invalid number -> null output
        assertNull(OSMChargingStation.parseOutputPower("22.0.1 kW"))

        // Valid output power values
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22 kW"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22 kVA"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22. kW"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22.0 kW"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22,0 kW"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22kW"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22    kW"))

        // number without unit, assume kW or W depending on the number's magnitude
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22.0"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22,0"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22000"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22000.0"))
        assertEquals(22.0, OSMChargingStation.parseOutputPower("22000,0"))
    }
}