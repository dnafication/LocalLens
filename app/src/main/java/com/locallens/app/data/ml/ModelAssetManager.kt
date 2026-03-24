package com.locallens.app.data.ml

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelAssetManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val FACENET_MODEL = "facenet.tflite"
    }

    fun isFaceNetModelAvailable(): Boolean {
        return try {
            context.assets.open(FACENET_MODEL).use { true }
        } catch (e: Exception) {
            false
        }
    }

    fun getModelPath(): String = FACENET_MODEL
}
