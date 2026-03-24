package com.locallens.app.data.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.absoluteValue

@Singleton
class FaceDetectorWrapper @Inject constructor() {

    private val detector: com.google.mlkit.vision.face.FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
            .build()
        FaceDetection.getClient(options)
    }

    suspend fun detect(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces -> cont.resume(faces) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    companion object {
        private const val MIN_FACE_SIZE_PX = 48
        private const val MAX_YAW_ANGLE = 45f
        private const val MAX_PITCH_ANGLE = 30f
        private const val MIN_CONFIDENCE = 0.7f

        /**
         * Quality filter for face detections. Returns true if the face passes all quality checks.
         */
        fun passesQualityFilter(face: Face, imageWidth: Int, imageHeight: Int): Boolean {
            val bbox = face.boundingBox
            if (bbox.width() < MIN_FACE_SIZE_PX || bbox.height() < MIN_FACE_SIZE_PX) return false
            if (face.headEulerAngleY.absoluteValue > MAX_YAW_ANGLE) return false
            if (face.headEulerAngleX.absoluteValue > MAX_PITCH_ANGLE) return false
            return true
        }
    }
}
