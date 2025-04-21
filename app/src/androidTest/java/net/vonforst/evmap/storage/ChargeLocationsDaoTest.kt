package net.vonforst.evmap.storage

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.ChargeLocationCluster
import net.vonforst.evmap.model.ChargepointListItem
import net.vonforst.evmap.model.Coordinate
import net.vonforst.evmap.ui.cluster
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ChargeLocationsDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ChargeLocationsDao

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        dao = database.chargeLocationsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testClustering() {
        val lat1 = 53.0
        val lng1 = 9.0
        val lat2 = 54.0
        val lng2 = 10.0

        val chargeLocations = (0..100).map { i ->
            val lat = Random.nextDouble(lat1, lat2)
            val lng = Random.nextDouble(lng1, lng2)
            ChargeLocation(
                i.toLong(),
                "test",
                "test",
                Coordinate(lat, lng),
                null,
                emptyList(),
                null,
                "https://google.com",
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null, null, null, null, null, null, null, Instant.now(), true
            )
        }
        runBlocking {
            dao.insert(*chargeLocations.toTypedArray())
        }

        val zoom = 10f

        val clusteredInMemory = cluster(chargeLocations, zoom).sorted()
        val clusteredInDB = runBlocking {
            dao.getChargeLocationsClustered(lat1, lat2, lng1, lng2, "test", 0L, zoom)
        }.sorted()
        assertEquals(clusteredInMemory.size, clusteredInDB.size)
        clusteredInDB.zip(clusteredInMemory).forEach { (a, b) ->
            when (a) {
                is ChargeLocation -> {
                    assertTrue(b is ChargeLocation)
                    assertEquals(a, b)
                }
                is ChargeLocationCluster -> {
                    assertTrue(b is ChargeLocationCluster)
                    assertEquals(a.clusterCount, (b as ChargeLocationCluster).clusterCount)
                    assertEquals(a.coordinates.lat, b.coordinates.lat, 1e-5)
                    assertEquals(a.coordinates.lng, b.coordinates.lng, 1e-5)
                }
            }
        }
    }

    private fun List<ChargepointListItem>.sorted() = sortedBy {
        when (it) {
            is ChargeLocationCluster -> it.coordinates.lat
            is ChargeLocation -> it.coordinates.lat
            else -> 0.0
        }
    }.sortedBy {
        when (it) {
            is ChargeLocationCluster -> it.coordinates.lng
            is ChargeLocation -> it.coordinates.lng
            else -> 0.0
        }
    }
}