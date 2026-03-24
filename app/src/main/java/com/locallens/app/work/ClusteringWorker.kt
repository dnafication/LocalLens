package com.locallens.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.locallens.app.data.clustering.DbscanClusterer
import com.locallens.app.data.repository.FaceRepository
import com.locallens.app.data.repository.PersonRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ClusteringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val faceRepo: FaceRepository,
    private val clusterer: DbscanClusterer,
    private val personRepo: PersonRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val unassigned = faceRepo.getUnassignedEmbeddings()
        if (unassigned.size < 5) return Result.success()

        val existingPersonCentroids = faceRepo.getPersonCentroids()
        val result = clusterer.cluster(unassigned, existingPersonCentroids)
        personRepo.applyClusteringResult(result)
        return Result.success()
    }
}
