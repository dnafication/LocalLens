package com.locallens.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.IndexState
import com.locallens.app.data.media.VideoFrameExtractor
import com.locallens.app.data.ml.FaceDetectorWrapper
import com.locallens.app.data.ml.FaceEmbedder
import com.locallens.app.data.repository.MediaRepository
import com.locallens.app.util.BitmapUtils
import com.locallens.app.util.EmbeddingUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MediaIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepo: MediaRepository,
    private val faceDetector: FaceDetectorWrapper,
    private val faceEmbedder: FaceEmbedder,
    private val videoExtractor: VideoFrameExtractor
) : CoroutineWorker(context, params) {

    companion object {
        const val BATCH_SIZE = 20
        const val KEY_MEDIA_FILE_IDS = "media_file_ids"
    }

    override suspend fun doWork(): Result {
        val ids = inputData.getLongArray(KEY_MEDIA_FILE_IDS) ?: return Result.failure()

        for (mediaFileId in ids) {
            try {
                processMediaFile(mediaFileId)
            } catch (e: Exception) {
                mediaRepo.markError(mediaFileId, e.message ?: "Unknown error")
            }
        }

        return Result.success()
    }

    private suspend fun processMediaFile(id: Long) {
        val mediaFile = mediaRepo.getById(id) ?: return
        mediaRepo.markProcessing(id)

        val frames = when {
            mediaFile.mimeType.startsWith("image/") -> {
                try {
                    listOf(Pair(0L, BitmapUtils.loadAndOrient(mediaFile.filePath)))
                } catch (e: Exception) {
                    mediaRepo.markError(id, "Failed to load image: ${e.message}")
                    return
                }
            }
            mediaFile.mimeType.startsWith("video/") -> {
                videoExtractor.extractFrames(mediaFile.filePath, mediaFile.durationMs)
            }
            else -> {
                mediaRepo.markSkipped(id)
                return
            }
        }

        val seenEmbeddings = mutableListOf<FloatArray>()

        for ((timestampMs, bitmap) in frames) {
            try {
                val faces = faceDetector.detect(bitmap)
                for (face in faces) {
                    if (!FaceDetectorWrapper.passesQualityFilter(face, bitmap.width, bitmap.height)) continue

                    val crop = BitmapUtils.cropFace(bitmap, face.boundingBox, padding = 0.20f)
                    val embedding = faceEmbedder.embed(crop)

                    // Video deduplication
                    if (seenEmbeddings.any { EmbeddingUtils.cosineSimilarity(it, embedding) > 0.85f }) continue
                    seenEmbeddings.add(embedding)

                    val detection = FaceDetection(
                        frameTimestampMs = timestampMs,
                        boxLeft = face.boundingBox.left / bitmap.width.toFloat(),
                        boxTop = face.boundingBox.top / bitmap.height.toFloat(),
                        boxRight = face.boundingBox.right / bitmap.width.toFloat(),
                        boxBottom = face.boundingBox.bottom / bitmap.height.toFloat(),
                        detectionConfidence = face.trackingId?.toFloat() ?: 0f,
                        rollAngle = face.headEulerAngleZ,
                        yawAngle = face.headEulerAngleY,
                        pitchAngle = face.headEulerAngleX,
                        embedding = embedding,
                        embeddingVersion = 1
                    )
                    detection.mediaFile.targetId = id
                    mediaRepo.saveFaceDetection(detection)
                }
            } finally {
                bitmap.recycle()
            }
        }

        val state = if (seenEmbeddings.isEmpty()) IndexState.NO_FACES else IndexState.INDEXED
        mediaRepo.markIndexed(id, state)
    }
}
