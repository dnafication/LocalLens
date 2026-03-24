package com.locallens.app.data.clustering

import com.locallens.app.util.EmbeddingUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DbscanClusterer @Inject constructor() {

    companion object {
        const val EPSILON = 0.40f
        const val MIN_SAMPLES = 1
    }

    data class ClusteringInput(
        val faceDetectionId: Long,
        val embedding: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ClusteringInput
            return faceDetectionId == other.faceDetectionId
        }
        override fun hashCode(): Int = faceDetectionId.hashCode()
    }

    data class ClusteringResult(
        val assignments: Map<Long, Long>,
        val newClusters: Map<Long, FloatArray>
    )

    fun cluster(
        unassigned: List<ClusteringInput>,
        existingPersonCentroids: Map<Long, FloatArray>
    ): ClusteringResult {
        if (unassigned.isEmpty()) return ClusteringResult(emptyMap(), emptyMap())

        val assignments = mutableMapOf<Long, Long>()
        val remaining = mutableListOf<ClusteringInput>()

        // First: try to assign to existing persons
        for (face in unassigned) {
            var bestPersonId: Long? = null
            var bestDistance = Float.MAX_VALUE
            for ((personId, centroid) in existingPersonCentroids) {
                val distance = EmbeddingUtils.cosineDistance(face.embedding, centroid)
                if (distance < EPSILON && distance < bestDistance) {
                    bestDistance = distance
                    bestPersonId = personId
                }
            }
            if (bestPersonId != null) {
                assignments[face.faceDetectionId] = bestPersonId
            } else {
                remaining.add(face)
            }
        }

        // Second: run DBSCAN on remaining unassigned faces
        val dbscanResult = runDbscan(remaining)
        assignments.putAll(dbscanResult.assignments)

        return ClusteringResult(assignments, dbscanResult.newClusters)
    }

    internal fun runDbscan(points: List<ClusteringInput>): ClusteringResult {
        if (points.isEmpty()) return ClusteringResult(emptyMap(), emptyMap())

        val n = points.size
        val visited = BooleanArray(n)
        val clusterLabels = IntArray(n) { -1 }
        var currentCluster = 0

        for (i in 0 until n) {
            if (visited[i]) continue
            visited[i] = true

            val neighbors = regionQuery(points, i)
            if (neighbors.size >= MIN_SAMPLES) {
                expandCluster(points, i, neighbors, currentCluster, visited, clusterLabels)
                currentCluster++
            }
        }

        // Build result
        val assignments = mutableMapOf<Long, Long>()
        val clusterPoints = mutableMapOf<Int, MutableList<FloatArray>>()

        for (i in 0 until n) {
            if (clusterLabels[i] >= 0) {
                val clusterId = -(clusterLabels[i].toLong() + 1) // Negative IDs for new clusters
                assignments[points[i].faceDetectionId] = clusterId
                clusterPoints.getOrPut(clusterLabels[i]) { mutableListOf() }.add(points[i].embedding)
            }
        }

        val newClusters = clusterPoints.mapKeys { -(it.key.toLong() + 1) }
            .mapValues { (_, embeddings) -> EmbeddingUtils.computeCentroid(embeddings)!! }

        return ClusteringResult(assignments, newClusters)
    }

    private fun regionQuery(points: List<ClusteringInput>, pointIndex: Int): MutableList<Int> {
        val neighbors = mutableListOf<Int>()
        val embedding = points[pointIndex].embedding
        for (i in points.indices) {
            if (EmbeddingUtils.cosineDistance(embedding, points[i].embedding) < EPSILON) {
                neighbors.add(i)
            }
        }
        return neighbors
    }

    private fun expandCluster(
        points: List<ClusteringInput>,
        pointIndex: Int,
        neighbors: MutableList<Int>,
        clusterId: Int,
        visited: BooleanArray,
        clusterLabels: IntArray
    ) {
        clusterLabels[pointIndex] = clusterId
        var i = 0
        while (i < neighbors.size) {
            val neighborIdx = neighbors[i]
            if (!visited[neighborIdx]) {
                visited[neighborIdx] = true
                val newNeighbors = regionQuery(points, neighborIdx)
                if (newNeighbors.size >= MIN_SAMPLES) {
                    for (nn in newNeighbors) {
                        if (nn !in neighbors) {
                            neighbors.add(nn)
                        }
                    }
                }
            }
            if (clusterLabels[neighborIdx] < 0) {
                clusterLabels[neighborIdx] = clusterId
            }
            i++
        }
    }
}
