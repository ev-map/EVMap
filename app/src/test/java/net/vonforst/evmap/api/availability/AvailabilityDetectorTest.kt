package net.vonforst.evmap.api.availability

import net.vonforst.evmap.api.goingelectric.Chargepoint
import org.junit.Assert.assertEquals
import org.junit.Test

class AvailabilityDetectorTest {
    @Test
    fun testMatchChargepointsSingleCorrect() {
        // single charger with 2 22kW chargepoints
        val chargepoints = listOf(Chargepoint("Typ2", 22.0, 2))

        // correct data in NewMotion
        assertEquals(
            mapOf(chargepoints[0] to setOf(0L, 1L)),
            BaseAvailabilityDetector.matchChargepoints(
                mapOf(0L to (22.0 to "Typ2"), 1L to (22.0 to "Typ2")),
                chargepoints
            )
        )
    }

    @Test
    fun testMatchChargepointsSingleWrongPower() {
        // single charger with 2 22kW chargepoints
        val chargepoints = listOf(Chargepoint("Typ2", 22.0, 2))

        // wrong power in NewMotion
        assertEquals(
            mapOf(chargepoints[0] to setOf(0L, 1L)),
            BaseAvailabilityDetector.matchChargepoints(
                mapOf(0L to (27.0 to "Typ2"), 1L to (27.0 to "Typ2")),
                chargepoints
            )
        )
    }

    @Test(expected = AvailabilityDetectorException::class)
    fun testMatchChargepointsSingleWrong() {
        // single charger with 2 22kW chargepoints
        val chargepoints = listOf(Chargepoint("Typ2", 22.0, 2))

        // non-matching data in NewMotion
        BaseAvailabilityDetector.matchChargepoints(
            mapOf(0L to (27.0 to "Typ2"), 1L to (27.0 to "Typ2"), 2L to (50.0 to "CCS")),
            chargepoints
        )
    }

    @Test
    fun testMatchChargepointsComplex() {
        // charger with many different connectors
        val chargepoints = listOf(
            Chargepoint("Typ2", 43.0, 1),
            Chargepoint("CCS", 50.0, 1),
            Chargepoint("CHAdeMO", 50.0, 2),
            Chargepoint("CCS", 160.0, 1),
            Chargepoint("CCS", 320.0, 2)
        )

        // partly wrong power in NewMotion
        assertEquals(
            mapOf(
                chargepoints[0] to setOf(6L),
                chargepoints[1] to setOf(4L),
                chargepoints[2] to setOf(0L, 5L),
                chargepoints[3] to setOf(2L),
                chargepoints[4] to setOf(1L, 3L)
            ),
            BaseAvailabilityDetector.matchChargepoints(
                mapOf(
                    // CHAdeMO + CCS HPC
                    0L to (50.0 to "CHAdeMO"),
                    1L to (200.0 to "CCS"),
                    // dual CCS HPC
                    2L to (80.0 to "CCS"),
                    3L to (200.0 to "CCS"),
                    // 50kW triple charger
                    4L to (50.0 to "CCS"),
                    5L to (50.0 to "CHAdeMO"),
                    6L to (43.0 to "Typ2")
                ),
                chargepoints
            )
        )
    }

    @Test
    fun testMatchChargepointsDifferentPower() {
        // single charger with 1 22kW and 1 11kW chargepoint (common when load balancing is applied)
        val chargepoints = listOf(
            Chargepoint("Typ2", 22.0, 1),
            Chargepoint("Typ2", 11.0, 1)
        )

        // both have 27 kW power in NewMotion
        assertEquals(
            mapOf(chargepoints[1] to setOf(0L), chargepoints[0] to setOf(1L)),
            BaseAvailabilityDetector.matchChargepoints(
                mapOf(0L to (27.0 to "Typ2"), 1L to (27.0 to "Typ2")),
                chargepoints
            )
        )
    }

    @Test
    fun testMatchChargepointsDifferentPower2() {
        // two chargers with 1 22kW and 1 11kW chargepoint (common when load balancing is applied)
        val chargepoints = listOf(
            Chargepoint("Typ2", 22.0, 2),
            Chargepoint("Typ2", 11.0, 2)
        )

        // both have 27 kW power in NewMotion
        assertEquals(
            mapOf(chargepoints[1] to setOf(0L, 1L), chargepoints[0] to setOf(2L, 3L)),
            BaseAvailabilityDetector.matchChargepoints(
                mapOf(
                    0L to (27.0 to "Typ2"),
                    1L to (27.0 to "Typ2"),
                    2L to (27.0 to "Typ2"),
                    3L to (27.0 to "Typ2")
                ),
                chargepoints
            )
        )
    }
}