package com.locallens.app.data.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class VideoFrameExtractor @Inject constructor() {

    companion object {
        private const val MAX_FRAMES = 50
    }

    suspend fun extractFrames(filePath: String, durationMs: Long): List<Pair<Long, Bitmap>> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val timestamps = computeTimestamps(durationMs)
            timestamps.mapNotNull { tsMs ->
                val bitmap = retriever.getFrameAtTime(
                    tsMs * 1000, // Convert ms to microseconds
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (bitmap != null) Pair(tsMs, bitmap) else null
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            retriever.release()
        }
    }

    internal fun computeTimestamps(durationMs: Long): List<Long> {
        if (durationMs <= 0) return listOf(0L)

        val durationSec = durationMs / 1000.0

        return when {
            // Videos <= 10s: extract at 0s and 50%
            durationSec <= 10 -> listOf(0L, durationMs / 2)

            // Videos 10s-60s: every 5 seconds
            durationSec <= 60 -> {
                val intervalMs = 5000L
                generateTimestamps(durationMs, intervalMs)
            }

            // Videos 60s-300s: every 15 seconds
            durationSec <= 300 -> {
                val intervalMs = 15000L
                generateTimestamps(durationMs, intervalMs)
            }

            // Videos > 300s: every 30 seconds, capped at MAX_FRAMES
            else -> {
                val intervalMs = 30000L
                val timestamps = generateTimestamps(durationMs, intervalMs)
                timestamps.take(MAX_FRAMES)
            }
        }
    }

    private fun generateTimestamps(durationMs: Long, intervalMs: Long): List<Long> {
        val timestamps = mutableListOf<Long>()
        var current = 0L
        while (current < durationMs) {
            timestamps.add(current)
            current += intervalMs
        }
        return timestamps
    }
}
