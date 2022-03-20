package net.vonforst.evmap.api.openchargemap

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OpenChargeMapAdaptersTest {
    @Test
    fun testZonedDateTimeAdapter() {
        val adapter = ZonedDateTimeAdapter()
        assertEquals(
            ZonedDateTime.of(2022, 3, 19, 23, 24, 0, 0, ZoneOffset.UTC),
            adapter.fromJson("2022-03-19T23:24:00Z")
        )
        assertEquals(
            ZonedDateTime.of(2022, 3, 19, 23, 24, 0, 0, ZoneOffset.UTC),
            adapter.fromJson("2022-03-19T23:24:00")
        )
    }
}