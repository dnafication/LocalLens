package com.locallens.app.data.repository

import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.MediaFile
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getAllMediaFiles(): Flow<List<MediaFile>>
    fun getPendingFiles(): List<MediaFile>
    fun getById(id: Long): MediaFile?
    fun getByMediaStoreId(mediaStoreId: Long): MediaFile?
    fun upsert(mediaFile: MediaFile): Long
    fun markProcessing(id: Long)
    fun markIndexed(id: Long, state: Int)
    fun markError(id: Long, message: String)
    fun markSkipped(id: Long)
    fun saveFaceDetection(face: FaceDetection)
    fun deleteByMediaStoreId(mediaStoreId: Long)
}
