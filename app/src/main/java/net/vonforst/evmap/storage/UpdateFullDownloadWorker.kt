package net.vonforst.evmap.storage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.vonforst.evmap.api.createApi
import net.vonforst.evmap.api.openstreetmap.OSMReferenceData
import net.vonforst.evmap.api.openstreetmap.OpenStreetMapApiWrapper

class UpdateFullDownloadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val dataSource = PreferenceDataSource(applicationContext).dataSource
        val db = AppDatabase.getInstance(applicationContext)
        val chargeLocations = db.chargeLocationsDao()
        val api = createApi(dataSource, applicationContext)

        if (!api.supportsFullDownload) return Result.success()

        var insertJob: Job? = null
        val result = api.fullDownload()
        result.chargers.chunked(1024).forEach {
            insertJob?.join()
            insertJob = withContext(Dispatchers.IO) {
                launch {
                    chargeLocations.insert(*it.toTypedArray())
                }
            }
        }

        when (api) {
            is OpenStreetMapApiWrapper -> {
                val refData = result.referenceData
                val repo = OSMReferenceDataRepository(db.osmReferenceDataDao())
                repo.updateReferenceData(refData as OSMReferenceData)
            }
        }

        // TODO: remove deleted chargers
        return Result.success()
    }
}