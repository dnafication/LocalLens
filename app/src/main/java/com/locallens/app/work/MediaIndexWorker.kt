package com.locallens.app.work

import android.content.Context
import android.graphics.Bitmap
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.IndexState
import com.locallens.app.data.media.VideoFrameExtractor
import com.locallens.app.data.ml.FaceDetector
import com.locallens.app.data.ml.FaceEmbedder
import com.locallens.app.data.repository.MediaRepository
import com.locallens.app.util.BitmapUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.absoluteValue

@HiltWorker
class MediaIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepo: MediaRepository,
    private val faceDetector: FaceDetector,
    private val faceEmbedder: FaceEmbedder,
    private val videoExtractor: VideoFrameExtractor
) : CoroutineWorker(context, params) {

    companion object {
        const val BATCH_SIZE = 20
        const val KEY_MEDIA_FILE_IDS = "media_file_ids"
    }

    override suspend fun doWork(): Result {
        val ids = inputData.getLongArray(KEY_MEDIA_FILE_IDS) ?: return Result.failure()

        ids.forEach { mediaFileId ->
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

        val frames: List<Pair<Long, Bitmap>> = when {
            mediaFile.mimeType.startsWith("image/") -> {
                val bmp = BitmapUtils.loadAndOrient(mediaFile.filePath) ?: return mediaRepo.markSkipped(id)
                listOf(Pair(0L, bmp))
            }
            mediaFile.mimeType.startsWith("video/") ->
                videoExtractor.extractFrames(mediaFile.filePath, mediaFile.durationMs)
            else -> return mediaRepo.markSkipped(id)
        }

        val seenEmbeddings = mutableListOf<FloatArray>()

        for ((timestampMs, bitmap) in frames) {
            val faces = faceDetector.detect(bitmap)
            for (face in faces) {
                with(faceDetector) {
                    if (!face.passesQualityFilter(bitmap)) continue
                }

                val crop = BitmapUtils.cropFace(bitmap, face.boundingBox, padding = 0.20f)
                    ?: continue
                val embedding = faceEmbedder.embed(crop)

                if (seenEmbeddings.any { cosineSimilarity(it, embedding) > 0.85f }) continue
                seenEmbeddings.add(embedding)

                val detection = FaceDetection(
                    frameTimestampMs = timestampMs,
                    boxLeft = face.boundingBox.left / bitmap.width.toFloat(),
                    boxTop = face.boundingBox.top / bitmap.height.toFloat(),
                    boxRight = face.boundingBox.right / bitmap.width.toFloat(),
                    boxBottom = face.boundingBox.bottom / bitmap.height.toFloat(),
                    detectionConfidence = 1f - (face.headEulerAngleY.absoluteValue / 90f),
                    rollAngle = face.headEulerAngleZ,
                    yawAngle = face.headEulerAngleY,
                    pitchAngle = face.headEulerAngleX,
                    embedding = embedding,
                    embeddingVersion = 1
                )
                detection.mediaFile.targetId = id
                mediaRepo.saveFaceDetection(detection)
            }
        }

        val state = if (seenEmbeddings.isEmpty()) IndexState.NO_FACES else IndexState.INDEXED
        mediaRepo.markIndexed(id, state)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
