package com.locallens.app.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class FaceEmbedder @Inject constructor(private val context: Context) {

    private val EMBEDDING_DIM = 128
    private val INPUT_SIZE = 160

    private var gpuDelegate: GpuDelegate? = null

    private val interpreter: Interpreter by lazy {
        val model = FileUtil.loadMappedFile(context, "facenet.tflite")
        val options = Interpreter.Options().apply {
            try {
                gpuDelegate = GpuDelegate()
                addDelegate(gpuDelegate!!)
            } catch (e: Exception) {
                numThreads = 4
            }
            numThreads = 2
        }
        Interpreter(model, options)
    }

    fun embed(faceBitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val input = preprocessToFloatArray(scaled)
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }
        interpreter.run(input, output)
        return l2Normalize(output[0])
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

    private fun l2Normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0f) FloatArray(vec.size) { vec[it] / norm } else vec
    }

    fun close() {
        gpuDelegate?.close()
        interpreter.close()
    }
}
