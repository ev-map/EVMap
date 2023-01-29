package net.vonforst.evmap.storage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.vonforst.evmap.api.createApi
import java.time.Instant

class CleanupCacheWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)

        val chargeLocations = db.chargeLocationsDao()
        val savedRegionDao = db.savedRegionDao()
        val now = Instant.now()

        val dataSources = listOf("openchargemap", "goingelectric")
        for (dataSource in dataSources) {
            val api = createApi(dataSource, applicationContext)
            val limit = now.minus(api.cacheLimit).toEpochMilli()
            chargeLocations.deleteOutdatedIfNotFavorite(dataSource, limit)
            savedRegionDao.deleteOutdated(dataSource, limit)
        }
        return Result.success()
    }
}