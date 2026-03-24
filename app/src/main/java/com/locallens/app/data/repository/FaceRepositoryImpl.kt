package com.locallens.app.data.repository

import com.locallens.app.data.clustering.DbscanClusterer
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.MediaFile
import com.locallens.app.util.EmbeddingUtils
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore
) : FaceRepository {

    private val faceBox: Box<FaceDetection> by lazy { boxStore.boxFor(FaceDetection::class.java) }
    private val mediaBox: Box<MediaFile> by lazy { boxStore.boxFor(MediaFile::class.java) }

    override fun getByPerson(personId: Long): List<FaceDetection> {
        return faceBox.all.filter { it.person.targetId == personId }
    }

    override fun getByMediaFile(mediaFileId: Long): List<FaceDetection> {
        return faceBox.all.filter { it.mediaFile.targetId == mediaFileId }
    }

    override fun getUnassignedEmbeddings(): List<DbscanClusterer.ClusteringInput> {
        return faceBox.all
            .filter { it.person.targetId == 0L && it.embedding.isNotEmpty() }
            .map { DbscanClusterer.ClusteringInput(it.id, it.embedding) }
    }

    override fun searchByEmbedding(embedding: FloatArray, maxResults: Int): List<FaceDetection> {
        return faceBox.all
            .filter { it.embedding.isNotEmpty() }
            .sortedBy { EmbeddingUtils.cosineDistance(embedding, it.embedding) }
            .take(maxResults)
    }

    override fun getMediaFilesForPerson(personId: Long): Flow<List<MediaFile>> = flow {
        val faceDetections = getByPerson(personId)
        val mediaFileIds = faceDetections.map { it.mediaFile.targetId }.distinct()
        val mediaFiles = mediaFileIds.mapNotNull { mediaBox.get(it) }
            .sortedByDescending { it.dateAdded }
        emit(mediaFiles)
    }
}
