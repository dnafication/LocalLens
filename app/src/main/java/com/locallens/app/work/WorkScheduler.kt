package com.locallens.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    fun enqueuePendingWork(pendingIds: List<Long>) {
        val batches = pendingIds.chunked(MediaIndexWorker.BATCH_SIZE)
        val requests = batches.map { batch ->
            OneTimeWorkRequestBuilder<MediaIndexWorker>()
                .setInputData(workDataOf(MediaIndexWorker.KEY_MEDIA_FILE_IDS to batch.toLongArray()))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        }
        if (requests.isNotEmpty()) {
            workManager.enqueue(requests)
        }
    }

    fun enqueueIncrementalScan() {
        val request = PeriodicWorkRequestBuilder<IncrementalScanWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            "incremental_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueClustering() {
        val request = OneTimeWorkRequestBuilder<ClusteringWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueue(request)
    }
}
