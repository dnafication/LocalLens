package com.locallens.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Cloud sync is optional and requires Firebase setup
        // This worker will sync metadata (Person records, assignments) to Firestore
        // when cloud backup is enabled in settings.
        // Implementation deferred until Firebase is configured.
        return Result.success()
    }
}
