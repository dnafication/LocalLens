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
class FaceDetector @Inject constructor() {

    private val detector by lazy {
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

    fun Face.passesQualityFilter(bitmap: Bitmap): Boolean {
        val box = boundingBox
        val boxWidth = box.width()
        val boxHeight = box.height()
        if (boxWidth < 48 || boxHeight < 48) return false
        if (headEulerAngleY.absoluteValue > 45f) return false
        if (headEulerAngleX.absoluteValue > 30f) return false
        return true
    }
}
