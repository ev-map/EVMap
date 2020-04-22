package net.vonforst.evmap.api

import org.junit.Assert.assertEquals
import org.junit.Test


class UtilsTest {
    @Test
    fun testDistanceBetween() {
        assertEquals(129412.71, distanceBetween(54.0, 9.0, 53.0, 8.0), 0.01)
    }
}