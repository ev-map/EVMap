package net.vonforst.evmap.ui;

import com.car2go.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.ChargeLocationCluster
import net.vonforst.evmap.model.ChargepointListItem
import net.vonforst.evmap.model.Coordinate


fun cluster(
    locations: List<ChargeLocation>,
    zoom: Float,
    clusterDistance: Int
): List<ChargepointListItem> {
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
    }
}

private class ChargepointClusterItem(val charger: ChargeLocation) : ClusterItem {
    override fun getSnippet(): String? = null

    override fun getTitle(): String? = charger.name

    override fun getPosition(): LatLng = LatLng(charger.coordinates.lat, charger.coordinates.lng)

}