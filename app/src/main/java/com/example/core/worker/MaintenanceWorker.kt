package com.example.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.database.OrbitDatabase
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for trimming stale history older than 30 days and cleaning temp cache.
 */
class MaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val db = OrbitDatabase.getInstance(applicationContext)
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            db.historyDao().deleteOlderThan(cutoff)
            applicationContext.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_")) {
                    file.deleteRecursively()
                }
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "orbit_periodic_maintenance"
        const val UNIQUE_ONETIME_WORK = "orbit_onetime_maintenance"
    }
}
