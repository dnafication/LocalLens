package com.locallens.app.data.clustering

import com.locallens.app.data.db.entities.Person
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DbscanClusterer @Inject constructor() {

    private val EPSILON = 0.40f
    private val MIN_SAMPLES = 1

    data class ClusteringInput(
        val faceDetectionId: Long,
        val embedding: FloatArray
    )

    data class ClusteringResult(
        val assignments: Map<Long, Long>,
        val centroids: Map<Long, FloatArray>
    )

    fun cluster(
        unassigned: List<ClusteringInput>,
        existingPersons: List<Person>
    ): ClusteringResult {
        if (unassigned.isEmpty()) return ClusteringResult(emptyMap(), emptyMap())

        val remaining = mutableListOf<ClusteringInput>()
        val assignments = mutableMapOf<Long, Long>()

        for (face in unassigned) {
            val match = existingPersons.firstOrNull { person ->
                val centroid = computeCentroid(person) ?: return@firstOrNull false
                cosineDistance(face.embedding, centroid) < EPSILON
            }
            if (match != null) {
                assignments[face.faceDetectionId] = match.id
            } else {
                remaining.add(face)
            }
        }

        val dbscanResult = runDbscan(remaining)
        assignments.putAll(dbscanResult.assignments)

        return ClusteringResult(assignments, dbscanResult.centroids)
    }

    private fun computeCentroid(person: Person): FloatArray? = null // Implemented via repo

    private fun runDbscan(points: List<ClusteringInput>): ClusteringResult {
        if (points.isEmpty()) return ClusteringResult(emptyMap(), emptyMap())

        val n = points.size
        val visited = BooleanArray(n) { false }
        val clusterLabels = IntArray(n) { -1 }
        var currentCluster = 0

        for (i in 0 until n) {
            if (visited[i]) continue
            visited[i] = true
            val neighbors = getNeighbors(points, i)
            // +1 counts the point itself (sklearn convention)
            if (neighbors.size + 1 >= MIN_SAMPLES) {
                expandCluster(points, i, neighbors, currentCluster, clusterLabels, visited)
                currentCluster++
            }
        }

        val assignments = mutableMapOf<Long, Long>()
        val centroidMap = mutableMapOf<Long, FloatArray>()

        // Map cluster labels to synthetic person IDs (negative to avoid collisions)
        val clusterToPersonId = mutableMapOf<Int, Long>()
        var syntheticId = -1L

        for (i in 0 until n) {
            val label = clusterLabels[i]
            if (label == -1) continue // noise

            val personId = clusterToPersonId.getOrPut(label) { syntheticId-- }
            assignments[points[i].faceDetectionId] = personId
        }

        // Compute centroids
        for ((clusterId, personId) in clusterToPersonId) {
            val clusterPoints = (0 until n)
                .filter { clusterLabels[it] == clusterId }
                .map { points[it].embedding }
            centroidMap[personId] = computeMeanEmbedding(clusterPoints)
        }

        return ClusteringResult(assignments, centroidMap)
    }

    private fun getNeighbors(points: List<ClusteringInput>, idx: Int): MutableList<Int> {
        return points.indices.filter { j ->
            j != idx && cosineDistance(points[idx].embedding, points[j].embedding) < EPSILON
        }.toMutableList()
    }

    private fun expandCluster(
        points: List<ClusteringInput>,
        pointIdx: Int,
        neighbors: MutableList<Int>,
        cluster: Int,
        labels: IntArray,
        visited: BooleanArray
    ) {
        labels[pointIdx] = cluster
        var i = 0
        while (i < neighbors.size) {
            val neighborIdx = neighbors[i]
            if (!visited[neighborIdx]) {
                visited[neighborIdx] = true
                val newNeighbors = getNeighbors(points, neighborIdx)
                // +1 counts the neighbor itself as a core point candidate
                if (newNeighbors.size + 1 >= MIN_SAMPLES) {
                    neighbors.addAll(newNeighbors.filter { it !in neighbors })
                }
            }
            if (labels[neighborIdx] == -1) {
                labels[neighborIdx] = cluster
            }
            i++
        }
    }

    private fun computeMeanEmbedding(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return floatArrayOf()
        val dim = embeddings[0].size
        val mean = FloatArray(dim)
        for (emb in embeddings) {
            for (i in emb.indices) mean[i] += emb[i]
        }
        val count = embeddings.size.toFloat()
        return FloatArray(dim) { mean[it] / count }
    }

    fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return 1f - dot
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float = 1f - cosineDistance(a, b)
}
