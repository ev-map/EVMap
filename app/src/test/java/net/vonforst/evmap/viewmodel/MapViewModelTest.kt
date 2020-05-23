package net.vonforst.evmap.viewmodel

import org.junit.Test

class MapViewModelTest {
    @Test
    fun testGetClusterDistance() {
        var zoom = 0.0f
        var previousDistance: Int? = 999
        while (zoom < 20.0f) {
            val distance = getClusterDistance(zoom)
            if (previousDistance != null) {
                if (distance != null) {
                    assert(distance <= previousDistance)
                }
            } else {
                assert(distance == null)
            }

            previousDistance = distance
            zoom += 0.1f
        }
    }
}