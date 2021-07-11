package net.vonforst.evmap.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {
    @Test
    fun testPowerMapping() {
        val sliderValues = powerSteps.indices.toList()

        val mappedValues = (sliderValues).map(::mapPower)
        assertTrue(mappedValues.distinct() == mappedValues)
        assertEquals(350, mappedValues.last())
        assertEquals(0, mappedValues.first())

        val reverseMappedValues = mappedValues.map(::mapPowerInverse)
        assertEquals(sliderValues, reverseMappedValues)
    }

    @Test
    fun testPowerMappingInbetween() {
        val sliderValue = 54
        assertEquals(50, mapPower(mapPowerInverse(sliderValue)))
    }
}