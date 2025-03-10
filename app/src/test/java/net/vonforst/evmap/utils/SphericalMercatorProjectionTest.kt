package net.vonforst.evmap.utils

import co.anbora.labs.spatia.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Test


class SphericalMercatorProjectionTest {
    @Test
    fun testProject() {
        assertPointEquals(
            Point(-11169055.58, 2800000.00, 3857),
            SphericalMercatorProjection.project(Point(-100.33333333, 24.38178696)),
            0.01
        )
    }

    @Test
    fun testUnproject() {
        assertPointEquals(
            Point(-100.33333333, 24.38178696),
            SphericalMercatorProjection.unproject(Point(-11169055.58, 2800000.00, 3857)),
            0.01
        )
    }

    private fun assertPointEquals(
        expected: Point,
        projected: Point,
        delta: Double
    ) {
        assertEquals(expected.x, projected.x, delta)
        assertEquals(expected.y, projected.y, delta)
        assertEquals(expected.srid, projected.srid)
    }
}