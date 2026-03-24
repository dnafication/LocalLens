package com.locallens.app.util

import kotlin.math.sqrt

object EmbeddingUtils {

    /**
     * Compute cosine similarity between two embedding vectors.
     * Returns value in range [-1, 1] where 1 means identical.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding dimensions must match: ${a.size} vs ${b.size}" }
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    /**
     * Compute cosine distance between two embedding vectors.
     * Returns value in range [0, 2] where 0 means identical.
     */
    fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        return 1f - cosineSimilarity(a, b)
    }

    /**
     * L2-normalize a vector in-place.
     * After normalization, the vector has unit length.
     */
    fun l2Normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 0f) FloatArray(vec.size) { vec[it] / norm } else vec
    }

    /**
     * Compute the centroid (mean) of a list of embedding vectors.
     * The result is L2-normalized.
     */
    fun computeCentroid(embeddings: List<FloatArray>): FloatArray? {
        if (embeddings.isEmpty()) return null
        val dim = embeddings.first().size
        val sum = FloatArray(dim)
        for (emb in embeddings) {
            for (i in sum.indices) {
                sum[i] += emb[i]
            }
        }
        val count = embeddings.size.toFloat()
        for (i in sum.indices) {
            sum[i] /= count
        }
        return l2Normalize(sum)
    }
}
