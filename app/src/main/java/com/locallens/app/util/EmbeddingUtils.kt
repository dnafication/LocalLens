package com.locallens.app.util

import kotlin.math.sqrt

object EmbeddingUtils {

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    fun cosineDistance(a: FloatArray, b: FloatArray): Float = 1f - cosineSimilarity(a, b)

    fun l2Normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0f) FloatArray(vec.size) { vec[it] / norm } else vec
    }
}
