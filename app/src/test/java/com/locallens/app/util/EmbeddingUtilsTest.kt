package com.locallens.app.util

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.math.sqrt

class EmbeddingUtilsTest {

    @Test
    fun `cosineSimilarity of identical vectors returns 1`() {
        val vec = floatArrayOf(1f, 2f, 3f, 4f)
        val similarity = EmbeddingUtils.cosineSimilarity(vec, vec)
        assertEquals(1f, similarity, 0.0001f)
    }

    @Test
    fun `cosineSimilarity of orthogonal vectors returns 0`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val similarity = EmbeddingUtils.cosineSimilarity(a, b)
        assertEquals(0f, similarity, 0.0001f)
    }

    @Test
    fun `cosineSimilarity of opposite vectors returns -1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        val similarity = EmbeddingUtils.cosineSimilarity(a, b)
        assertEquals(-1f, similarity, 0.0001f)
    }

    @Test
    fun `cosineSimilarity throws on mismatched dimensions`() {
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertThrows<IllegalArgumentException> {
            EmbeddingUtils.cosineSimilarity(a, b)
        }
    }

    @Test
    fun `cosineDistance of identical vectors returns 0`() {
        val vec = floatArrayOf(1f, 2f, 3f)
        val distance = EmbeddingUtils.cosineDistance(vec, vec)
        assertEquals(0f, distance, 0.0001f)
    }

    @Test
    fun `cosineDistance of orthogonal vectors returns 1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val distance = EmbeddingUtils.cosineDistance(a, b)
        assertEquals(1f, distance, 0.0001f)
    }

    @Test
    fun `l2Normalize produces unit length vector`() {
        val vec = floatArrayOf(3f, 4f)
        val normalized = EmbeddingUtils.l2Normalize(vec)
        val norm = sqrt(normalized.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, norm, 0.0001f)
    }

    @Test
    fun `l2Normalize of zero vector returns zero vector`() {
        val vec = floatArrayOf(0f, 0f, 0f)
        val normalized = EmbeddingUtils.l2Normalize(vec)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), normalized, 0.0001f)
    }

    @Test
    fun `computeCentroid of single vector returns normalized vector`() {
        val embeddings = listOf(floatArrayOf(3f, 4f))
        val centroid = EmbeddingUtils.computeCentroid(embeddings)
        assertNotNull(centroid)
        val norm = sqrt(centroid!!.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, norm, 0.0001f)
        assertEquals(3f / 5f, centroid[0], 0.0001f)
        assertEquals(4f / 5f, centroid[1], 0.0001f)
    }

    @Test
    fun `computeCentroid of empty list returns null`() {
        val centroid = EmbeddingUtils.computeCentroid(emptyList())
        assertNull(centroid)
    }

    @Test
    fun `computeCentroid of two identical vectors returns same direction`() {
        val vec = floatArrayOf(1f, 0f, 0f)
        val centroid = EmbeddingUtils.computeCentroid(listOf(vec, vec))
        assertNotNull(centroid)
        assertEquals(1f, centroid!![0], 0.0001f)
        assertEquals(0f, centroid[1], 0.0001f)
        assertEquals(0f, centroid[2], 0.0001f)
    }
}
