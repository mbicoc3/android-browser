package com.example.feature.shield.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.OrbitApplication

/**
 * WorkManager worker syncing enabled EasyPrivacy, AdGuard, and Peter Lowe's filter lists
 * into app-private storage (`filesDir/filter_lists/`) and recompiling [OrbitFilterEngine].
 */
class FilterListSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OrbitApplication ?: return Result.failure()
        return runCatching {
            app.container.shieldRepository.performNetworkSync()
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_SYNC = "orbit_filter_list_periodic_sync"
        const val UNIQUE_ONETIME_SYNC = "orbit_filter_list_onetime_sync"
    }
}
