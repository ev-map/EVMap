package net.vonforst.evmap.storage

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.anbora.labs.spatia.geometry.Mbr
import co.anbora.labs.spatia.geometry.MultiPolygon
import kotlinx.coroutines.runBlocking
import net.vonforst.evmap.storage.AppDatabase
import net.vonforst.evmap.storage.SavedRegion
import net.vonforst.evmap.storage.SavedRegionDao
import net.vonforst.evmap.utils.distanceBetween
import net.vonforst.evmap.viewmodel.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class SavedRegionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SavedRegionDao

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        dao = database.savedRegionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testGetSavedRegion() {
        val ds = "test"

        val ts1 = ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val region1 = Mbr(9.0, 53.0, 10.0, 54.0, 4326).asPolygon()
        runBlocking {
            dao.insert(
                SavedRegion(
                    region1,
                    ds, ts1, null, false
                )
            )
        }
        assertEquals(region1, dao.getSavedRegion(ds, 0))
        runBlocking {
            assertTrue(dao.savedRegionCovers(53.1, 53.2, 9.1, 9.2, ds, 0).await())
            assertTrue(dao.savedRegionCoversRadius(53.05, 9.15, 10.0, ds, 0).await())
            assertFalse(dao.savedRegionCovers(52.1, 52.2, 9.1, 9.2, ds, 0).await())
        }

        val ts2 = ZonedDateTime.of(2023, 1, 1, 1, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val region2 = Mbr(9.0, 55.0, 10.0, 56.0, 4326).asPolygon()
        runBlocking {
            dao.insert(
                SavedRegion(
                    region2,
                    ds, ts2, null, false
                )
            )
        }
        assertEquals(MultiPolygon(listOf(region1, region2)), dao.getSavedRegion(ds, 0))
        assertEquals(region2, dao.getSavedRegion(ds, ts1.toEpochMilli()))

        runBlocking {
            assertTrue(dao.savedRegionCovers(53.1, 53.2, 9.1, 9.2, ds, 0).await())
            assertTrue(dao.savedRegionCoversRadius(53.05, 9.15, 10.0, ds, 0).await())
            assertFalse(dao.savedRegionCovers(53.1, 55.2, 9.1, 9.2, ds, 0).await())
        }
    }

    @Test
    fun testMakeCircle() {
        val lat = 53.0
        val lng = 10.0
        val radius = 10000.0
        val circle = runBlocking { dao.makeCircle(lat, lng, radius) }
        for (point in circle.points) {
            assertEquals(radius, distanceBetween(lat, lng, point.y, point.x), 10.0)
        }
    }
}