package com.locallens.app.data.repository

import com.locallens.app.data.clustering.DbscanClusterer
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.MediaFile
import kotlinx.coroutines.flow.Flow

interface FaceRepository {
    fun getByPerson(personId: Long): List<FaceDetection>
    fun getByMediaFile(mediaFileId: Long): List<FaceDetection>
    fun getUnassignedEmbeddings(): List<DbscanClusterer.ClusteringInput>
    fun searchByEmbedding(embedding: FloatArray, maxResults: Int = 10): List<FaceDetection>
    fun getMediaFilesForPerson(personId: Long): Flow<List<MediaFile>>
}
