package com.locallens.app.data.repository

import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.IndexState
import com.locallens.app.data.db.entities.MediaFile
import com.locallens.app.data.db.entities.MediaFile_
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(private val boxStore: BoxStore) {

    private val mediaBox get() = boxStore.boxFor(MediaFile::class.java)
    private val faceBox get() = boxStore.boxFor(FaceDetection::class.java)

    fun getAllMediaFiles(): Flow<List<MediaFile>> = flow {
        emit(mediaBox.all)
    }

    fun getPendingFiles(): List<MediaFile> {
        return mediaBox.query(MediaFile_.indexState.equal(IndexState.PENDING.toLong())).build().find()
    }

    fun getById(id: Long): MediaFile? = mediaBox.get(id)

    fun upsert(mediaFile: MediaFile): Long {
        return mediaBox.put(mediaFile)
    }

    fun markProcessing(id: Long) {
        val file = mediaBox.get(id) ?: return
        file.indexState = IndexState.PROCESSING
        mediaBox.put(file)
    }

    fun markIndexed(id: Long, state: Int) {
        val file = mediaBox.get(id) ?: return
        file.indexState = state
        file.indexedAt = System.currentTimeMillis()
        mediaBox.put(file)
    }

    fun markError(id: Long, message: String) {
        val file = mediaBox.get(id) ?: return
        file.indexState = IndexState.ERROR
        file.errorMessage = message
        mediaBox.put(file)
    }

    fun markSkipped(id: Long) {
        val file = mediaBox.get(id) ?: return
        file.indexState = IndexState.SKIPPED
        mediaBox.put(file)
    }

    fun saveFaceDetection(face: FaceDetection) {
        faceBox.put(face)
    }

    fun deleteByMediaStoreId(mediaStoreId: Long) {
        val files = mediaBox.query(MediaFile_.mediaStoreId.equal(mediaStoreId)).build().find()
        for (file in files) {
            file.faces.forEach { face -> faceBox.remove(face.id) }
            mediaBox.remove(file.id)
        }
    }

    fun getByMediaStoreId(mediaStoreId: Long): MediaFile? {
        return mediaBox.query(MediaFile_.mediaStoreId.equal(mediaStoreId)).build().findFirst()
    }
}
