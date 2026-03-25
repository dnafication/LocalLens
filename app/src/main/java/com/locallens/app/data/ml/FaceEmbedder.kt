package com.locallens.app.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.locallens.app.util.EmbeddingUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceEmbedder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val EMBEDDING_DIM = 128
        const val INPUT_SIZE = 160
        private const val MODEL_FILE = "facenet.tflite"
    }

    private val interpreter: Interpreter? by lazy {
        try {
            val model = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                numThreads = 2
            }
            Interpreter(model, options)
        } catch (e: Exception) {
            null
        }
    }

    fun isModelAvailable(): Boolean = interpreter != null

    fun embed(faceBitmap: Bitmap): FloatArray {
        val interp = interpreter
            ?: throw IllegalStateException("FaceNet model not available. Place $MODEL_FILE in assets/")

        val scaled = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val input = preprocessToFloatArray(scaled)
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }
        interp.run(input, output)
        return EmbeddingUtils.l2Normalize(output[0])
    }

    private fun preprocessToFloatArray(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val input = Array(1) { Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(3) } } }
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                input[0][y][x][0] = (Color.red(pixel) - 128f) / 128f
                input[0][y][x][1] = (Color.green(pixel) - 128f) / 128f
                input[0][y][x][2] = (Color.blue(pixel) - 128f) / 128f
            }
        }
        return input
    }
}
