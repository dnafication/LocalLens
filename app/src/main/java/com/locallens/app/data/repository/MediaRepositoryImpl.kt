package com.locallens.app.data.repository

import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.IndexState
import com.locallens.app.data.db.entities.MediaFile
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore
) : MediaRepository {

    private val mediaBox: Box<MediaFile> by lazy { boxStore.boxFor(MediaFile::class.java) }
    private val faceBox: Box<FaceDetection> by lazy { boxStore.boxFor(FaceDetection::class.java) }

    override fun getAllMediaFiles(): Flow<List<MediaFile>> = flow {
        emit(mediaBox.all.sortedByDescending { it.dateAdded })
    }

    override fun getPendingFiles(): List<MediaFile> {
        return mediaBox.all.filter { it.indexState == IndexState.PENDING }
    }

    override fun getById(id: Long): MediaFile? {
        return mediaBox.get(id)
    }

    override fun getByMediaStoreId(mediaStoreId: Long): MediaFile? {
        return mediaBox.all.firstOrNull { it.mediaStoreId == mediaStoreId }
    }

    override fun upsert(mediaFile: MediaFile): Long {
        val existing = getByMediaStoreId(mediaFile.mediaStoreId)
        if (existing != null) {
            mediaFile.id = existing.id
            mediaFile.indexState = existing.indexState
            mediaFile.indexedAt = existing.indexedAt
        }
        return mediaBox.put(mediaFile)
    }

    override fun markProcessing(id: Long) {
        val file = mediaBox.get(id) ?: return
        file.indexState = IndexState.PROCESSING
        mediaBox.put(file)
    }

    override fun markIndexed(id: Long, state: Int) {
        val file = mediaBox.get(id) ?: return
        file.indexState = state
        file.indexedAt = System.currentTimeMillis()
        mediaBox.put(file)
    }

    override fun markError(id: Long, message: String) {
        val file = mediaBox.get(id) ?: return
        file.indexState = IndexState.ERROR
        file.errorMessage = message
        mediaBox.put(file)
    }

    override fun markSkipped(id: Long) {
        val file = mediaBox.get(id) ?: return
        file.indexState = IndexState.SKIPPED
        mediaBox.put(file)
    }

    override fun saveFaceDetection(face: FaceDetection) {
        faceBox.put(face)
    }

    override fun deleteByMediaStoreId(mediaStoreId: Long) {
        val file = getByMediaStoreId(mediaStoreId) ?: return
        // Delete associated face detections
        val faces = file.faces
        faceBox.remove(faces)
        mediaBox.remove(file)
    }
}
