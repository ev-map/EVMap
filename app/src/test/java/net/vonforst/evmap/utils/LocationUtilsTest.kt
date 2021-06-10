package net.vonforst.evmap.utils

import org.junit.Assert.assertEquals
import org.junit.Test


class LocationUtilsTest {
    @Test
    fun testDistanceBetween() {
        assertEquals(129521.08, distanceBetween(54.0, 9.0, 53.0, 8.0), 0.01)
    }

    @Test
    fun testStringToCoords() {
        assertEquals(listOf(52.515577, 13.379907), stringToCoords("52.515577,13.379907"))
        assertEquals(null, stringToCoords("52.515577,13.379907,57.123456"))
        assertEquals(null, stringToCoords("Hello, world."))
    }
}