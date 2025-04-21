package net.vonforst.evmap.ui

import co.anbora.labs.spatia.geometry.Point
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.ChargeLocationCluster
import net.vonforst.evmap.model.ChargepointListItem
import net.vonforst.evmap.model.Coordinate
import net.vonforst.evmap.utils.SphericalMercatorProjection
import kotlin.math.pow
import kotlin.math.roundToInt

fun getClusterPrecision(zoom: Float) = 30000000 / 2.0.pow(zoom.roundToInt() + 1)


fun cluster(
    locations: List<ChargeLocation>,
    zoom: Float
): List<ChargepointListItem> {
    val clusters = mutableMapOf<Pair<Int, Int>, MutableSet<ChargeLocation>>()
    val precision = getClusterPrecision(zoom)
    locations.forEach {
        // snap coordinates to grid
        val gridX = (it.coordinatesProjected.x / precision).roundToInt()
        val gridY = (it.coordinatesProjected.y / precision).roundToInt()
        clusters.getOrPut(gridX to gridY, ::mutableSetOf).add(it)
    }
    return clusters.map {
        if (it.value.size == 1) {
            it.value.first()
        } else {
            val centerX = it.value.map { it.coordinatesProjected.x }.average()
            val centerY = it.value.map { it.coordinatesProjected.y }.average()
            val centerLatLng = SphericalMercatorProjection.unproject(Point(centerX, centerY, 3857))
            ChargeLocationCluster(it.value.size, Coordinate(centerLatLng.y, centerLatLng.x))
        }
    }
}