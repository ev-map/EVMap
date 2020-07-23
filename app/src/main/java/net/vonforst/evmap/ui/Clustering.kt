package net.vonforst.evmap.ui;

import com.car2go.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import net.vonforst.evmap.api.goingelectric.ChargeLocation
import net.vonforst.evmap.api.goingelectric.ChargeLocationCluster
import net.vonforst.evmap.api.goingelectric.ChargepointListItem
import net.vonforst.evmap.api.goingelectric.Coordinate


fun cluster(
    result: List<ChargepointListItem>,
    zoom: Float,
    clusterDistance: Int
): List<ChargepointListItem> {
    val clusters = result.filterIsInstance<ChargeLocationCluster>()
    val locations = result.filterIsInstance<ChargeLocation>()

    val clusterItems = locations.map { ChargepointClusterItem(it) }

    val algo = NonHierarchicalDistanceBasedAlgorithm<ChargepointClusterItem>()
    algo.maxDistanceBetweenClusteredItems = clusterDistance
    algo.addItems(clusterItems)
    return algo.getClusters(zoom).map {
        if (it.size == 1) {
            it.items.first().charger
        } else {
            ChargeLocationCluster(it.size, Coordinate(it.position.latitude, it.position.longitude))
        }
    } + clusters
}

private class ChargepointClusterItem(val charger: ChargeLocation) : ClusterItem {
    override fun getSnippet(): String? = null

    override fun getTitle(): String? = charger.name

    override fun getPosition(): LatLng = LatLng(charger.coordinates.lat, charger.coordinates.lng)

}