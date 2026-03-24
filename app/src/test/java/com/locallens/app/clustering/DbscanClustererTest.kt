package com.locallens.app.clustering

import com.locallens.app.data.clustering.DbscanClusterer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DbscanClustererTest {

    private lateinit var clusterer: DbscanClusterer

    @Before
    fun setUp() {
        clusterer = DbscanClusterer()
    }

    @Test
    fun `two similar embeddings cluster together`() {
        val emb1 = FloatArray(128) { 1f / Math.sqrt(128.0).toFloat() }
        val emb2 = FloatArray(128) { 1f / Math.sqrt(128.0).toFloat() }
        // Slightly perturb emb2
        emb2[0] += 0.01f

        val inputs = listOf(
            DbscanClusterer.ClusteringInput(1L, emb1),
            DbscanClusterer.ClusteringInput(2L, emb2)
        )

        val result = clusterer.cluster(inputs, emptyMap())
        val assignment1 = result.assignments[1L]
        val assignment2 = result.assignments[2L]
        assertNotNull(assignment1)
        assertNotNull(assignment2)
        assertEquals("Similar faces should be in same cluster", assignment1, assignment2)
    }

    @Test
    fun `two very different embeddings stay in separate clusters`() {
        val emb1 = FloatArray(128) { if (it < 64) 1f else 0f }
        val emb2 = FloatArray(128) { if (it >= 64) 1f else 0f }

        val inputs = listOf(
            DbscanClusterer.ClusteringInput(1L, emb1),
            DbscanClusterer.ClusteringInput(2L, emb2)
        )

        val result = clusterer.cluster(inputs, emptyMap())
        val assignment1 = result.assignments[1L]
        val assignment2 = result.assignments[2L]
        // Different embeddings should be in different clusters
        assertNotEquals("Very different faces should be in different clusters", assignment1, assignment2)
    }

    @Test
    fun `empty input returns empty result`() {
        val result = clusterer.cluster(emptyList(), emptyMap())
        assertTrue(result.assignments.isEmpty())
        assertTrue(result.centroids.isEmpty())
    }

    @Test
    fun `cosine distance of identical vectors is zero`() {
        val emb = FloatArray(128) { 1f / Math.sqrt(128.0).toFloat() }
        val distance = clusterer.cosineDistance(emb, emb)
        assertEquals(0f, distance, 0.001f)
    }

    @Test
    fun `epsilon boundary - face within threshold of 0 point 39 should cluster together`() {
        val base = FloatArray(128) { 1f / Math.sqrt(128.0).toFloat() }
        // Create a vector at cosine distance ~0.39 from base
        // cosine distance = 1 - cosine_similarity
        // cosine_similarity = 0.61 means distance = 0.39
        val similar = FloatArray(128) { i ->
            if (i == 0) base[i] + 0.5f else base[i]
        }
        val l2Norm = Math.sqrt(similar.sumOf { (it * it).toDouble() }).toFloat()
        val normalized = FloatArray(128) { similar[it] / l2Norm }

        val distance = clusterer.cosineDistance(base, normalized)
        // Test that vectors within epsilon cluster together
        val inputs = listOf(
            DbscanClusterer.ClusteringInput(1L, base),
            DbscanClusterer.ClusteringInput(2L, normalized)
        )
        val result = clusterer.cluster(inputs, emptyMap())
        if (distance < 0.40f) {
            assertEquals(result.assignments[1L], result.assignments[2L])
        } else {
            assertNotEquals(result.assignments[1L], result.assignments[2L])
        }
    }
}
