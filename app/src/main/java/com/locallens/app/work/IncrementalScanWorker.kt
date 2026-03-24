package com.locallens.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.locallens.app.data.db.entities.ScanCheckpoint
import com.locallens.app.data.media.MediaStoreScanner
import com.locallens.app.data.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.collect

@HiltWorker
class IncrementalScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanner: MediaStoreScanner,
    private val mediaRepo: MediaRepository,
    private val boxStore: BoxStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val checkpointBox = boxStore.boxFor(ScanCheckpoint::class.java)
        val checkpoint = checkpointBox.all.firstOrNull() ?: ScanCheckpoint()
        val sinceMs = checkpoint.lastScannedDateModified

        var maxModified = sinceMs

        scanner.scanSince(sinceMs).collect { mediaFile ->
            val existing = mediaRepo.getByMediaStoreId(mediaFile.mediaStoreId)
            if (existing == null) {
                mediaRepo.upsert(mediaFile)
            }
            if (mediaFile.dateModified > maxModified) {
                maxModified = mediaFile.dateModified
            }
        }

        checkpoint.lastScannedDateModified = maxModified
        checkpoint.lastScanCompletedAt = System.currentTimeMillis()
        checkpointBox.put(checkpoint)

        return Result.success()
    }
}
