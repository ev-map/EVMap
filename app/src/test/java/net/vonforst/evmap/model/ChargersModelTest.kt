package net.vonforst.evmap.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChargersModelTest {
    @Test
    fun testAddressToString() {
        assertEquals("Berlin", Address("Berlin", null, null, null).toString())
        assertEquals("12345 Berlin", Address("Berlin", null, "12345", null).toString())
        assertEquals(
            "Pariser Platz 1, Berlin",
            Address("Berlin", null, null, "Pariser Platz 1").toString()
        )
        assertEquals(
            "Pariser Platz 1, 12345 Berlin",
            Address("Berlin", null, "12345", "Pariser Platz 1").toString()
        )
    }

}