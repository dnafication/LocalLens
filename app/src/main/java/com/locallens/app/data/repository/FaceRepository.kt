package com.locallens.app.data.repository

import com.locallens.app.data.clustering.DbscanClusterer
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.FaceDetection_
import com.locallens.app.data.db.entities.MediaFile
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceRepository @Inject constructor(private val boxStore: BoxStore) {

    private val faceBox get() = boxStore.boxFor(FaceDetection::class.java)
    private val mediaBox get() = boxStore.boxFor(MediaFile::class.java)

    fun getByPerson(personId: Long): List<FaceDetection> {
        return faceBox.query(FaceDetection_.personId.equal(personId)).build().find()
    }

    fun getByMediaFile(mediaFileId: Long): List<FaceDetection> {
        return faceBox.query(FaceDetection_.mediaFileId.equal(mediaFileId)).build().find()
    }

    fun getUnassignedEmbeddings(): List<DbscanClusterer.ClusteringInput> {
        return faceBox.query(FaceDetection_.personId.equal(0)).build().find()
            .filter { it.embedding.isNotEmpty() }
            .map { DbscanClusterer.ClusteringInput(it.id, it.embedding) }
    }

    fun searchByEmbedding(embedding: FloatArray, maxResults: Int = 10): List<FaceDetection> {
        return faceBox.all.sortedBy { face ->
            if (face.embedding.isEmpty()) Float.MAX_VALUE
            else cosineDistance(face.embedding, embedding)
        }.take(maxResults)
    }

    fun getMediaFilesForPerson(personId: Long): Flow<List<MediaFile>> = flow {
        val faces = faceBox.query(FaceDetection_.personId.equal(personId)).build().find()
        val mediaFileIds = faces.map { it.mediaFile.targetId }.distinct()
        val mediaFiles = mediaFileIds.mapNotNull { mediaBox.get(it) }
        emit(mediaFiles)
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return 1f - dot
    }
}
