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
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach

@HiltWorker
class IncrementalScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanner: MediaStoreScanner,
    private val mediaRepo: MediaRepository,
    private val boxStore: BoxStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val checkpointBox: Box<ScanCheckpoint> = boxStore.boxFor(ScanCheckpoint::class.java)
        val checkpoint = checkpointBox.all.firstOrNull() ?: ScanCheckpoint()

        checkpoint.lastScanStartedAt = System.currentTimeMillis()
        var maxDateModified = checkpoint.lastScannedDateModified
        var scannedCount = 0L

        scanner.scanSince(checkpoint.lastScannedDateModified)
            .onEach { mediaFile ->
                mediaRepo.upsert(mediaFile)
                scannedCount++
                if (mediaFile.dateModified > maxDateModified) {
                    maxDateModified = mediaFile.dateModified
                }
            }
            .collect()

        checkpoint.lastScannedDateModified = maxDateModified
        checkpoint.lastScanCompletedAt = System.currentTimeMillis()
        checkpoint.totalMediaScanned += scannedCount
        checkpointBox.put(checkpoint)

        return Result.success()
    }
}
