package net.vonforst.evmap.utils

import org.junit.Assert.assertEquals
import org.junit.Test


class LocationUtilsTest {
    @Test
    fun testDistanceBetween() {
        assertEquals(129521.08, distanceBetween(54.0, 9.0, 53.0, 8.0), 0.01)
    }
}