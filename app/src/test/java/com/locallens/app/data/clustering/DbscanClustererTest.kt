package com.locallens.app.data.clustering

import com.locallens.app.util.EmbeddingUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbscanClustererTest {

    private val clusterer = DbscanClusterer()

    private fun makeEmbedding(vararg values: Float): FloatArray {
        return EmbeddingUtils.l2Normalize(values)
    }

    @Test
    fun `empty input returns empty result`() {
        val result = clusterer.cluster(emptyList(), emptyMap())
        assertTrue(result.assignments.isEmpty())
        assertTrue(result.newClusters.isEmpty())
    }

    @Test
    fun `two similar embeddings cluster together`() {
        val emb1 = makeEmbedding(1f, 0f, 0f)
        val emb2 = makeEmbedding(0.95f, 0.05f, 0f)

        val inputs = listOf(
            DbscanClusterer.ClusteringInput(1L, emb1),
            DbscanClusterer.ClusteringInput(2L, emb2)
        )

        val result = clusterer.cluster(inputs, emptyMap())
        // Both should be assigned to the same cluster
        assertTrue(result.assignments.containsKey(1L))
        assertTrue(result.assignments.containsKey(2L))
        assertEquals(result.assignments[1L], result.assignments[2L])
    }

    @Test
    fun `two distant embeddings form different clusters`() {
        val emb1 = makeEmbedding(1f, 0f, 0f)
        val emb2 = makeEmbedding(0f, 1f, 0f)  // Orthogonal => cosine distance = 1.0

        val inputs = listOf(
            DbscanClusterer.ClusteringInput(1L, emb1),
            DbscanClusterer.ClusteringInput(2L, emb2)
        )

        val result = clusterer.cluster(inputs, emptyMap())
        assertTrue(result.assignments.containsKey(1L))
        assertTrue(result.assignments.containsKey(2L))
        assertNotEquals(result.assignments[1L], result.assignments[2L])
    }

    @Test
    fun `face assigned to existing person when close enough`() {
        val personCentroid = makeEmbedding(1f, 0f, 0f)
        val faceEmbedding = makeEmbedding(0.95f, 0.05f, 0f)

        val existingCentroids = mapOf(42L to personCentroid)
        val inputs = listOf(DbscanClusterer.ClusteringInput(1L, faceEmbedding))

        val result = clusterer.cluster(inputs, existingCentroids)
        assertEquals(42L, result.assignments[1L])
    }

    @Test
    fun `face not assigned to existing person when too far`() {
        val personCentroid = makeEmbedding(1f, 0f, 0f)
        val faceEmbedding = makeEmbedding(0f, 1f, 0f)  // Orthogonal

        val existingCentroids = mapOf(42L to personCentroid)
        val inputs = listOf(DbscanClusterer.ClusteringInput(1L, faceEmbedding))

        val result = clusterer.cluster(inputs, existingCentroids)
        // Should NOT be assigned to person 42
        assertNotEquals(42L, result.assignments[1L])
    }

    @Test
    fun `epsilon boundary - distance 0_39 should cluster together`() {
        // Two vectors with cosine distance ~0.39 (< EPSILON=0.40)
        // cos(theta)=0.61 => we need vectors with dot product ~0.61
        val emb1 = makeEmbedding(1f, 0f, 0f, 0f)
        // Create a vector at angle that gives cosine distance ~0.39
        val emb2 = makeEmbedding(0.8f, 0.6f, 0f, 0f)  // cos(dist) = 0.8/1.0 = 0.8, dist = 0.2

        val inputs = listOf(
            DbscanClusterer.ClusteringInput(1L, emb1),
            DbscanClusterer.ClusteringInput(2L, emb2)
        )

        val dist = EmbeddingUtils.cosineDistance(emb1, emb2)
        assertTrue(dist < DbscanClusterer.EPSILON, "Distance $dist should be < ${DbscanClusterer.EPSILON}")

        val result = clusterer.cluster(inputs, emptyMap())
        assertEquals(result.assignments[1L], result.assignments[2L], "Should cluster together at distance $dist")
    }

    @Test
    fun `clustering creates new clusters for unmatched faces`() {
        val emb1 = makeEmbedding(1f, 0f, 0f)
        val emb2 = makeEmbedding(0.98f, 0.02f, 0f)
        val emb3 = makeEmbedding(0f, 1f, 0f)

        val inputs = listOf(
            DbscanClusterer.ClusteringInput(1L, emb1),
            DbscanClusterer.ClusteringInput(2L, emb2),
            DbscanClusterer.ClusteringInput(3L, emb3)
        )

        val result = clusterer.cluster(inputs, emptyMap())
        // emb1 and emb2 should be in the same cluster
        assertEquals(result.assignments[1L], result.assignments[2L])
        // emb3 should be in a different cluster
        assertNotEquals(result.assignments[1L], result.assignments[3L])
    }
}
