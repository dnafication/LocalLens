package com.locallens.app.data.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import javax.inject.Inject

class VideoFrameExtractor @Inject constructor() {

    suspend fun extractFrames(filePath: String, durationMs: Long): List<Pair<Long, Bitmap>> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val timestamps = computeTimestamps(durationMs)
            timestamps.mapNotNull { ts ->
                val bmp = retriever.getFrameAtTime(
                    ts * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (bmp != null) Pair(ts, bmp) else null
            }
        } finally {
            retriever.release()
        }
    }

    private fun computeTimestamps(durationMs: Long): List<Long> {
        return when {
            durationMs <= 10_000L -> listOf(0L, durationMs / 2)
            durationMs <= 60_000L -> (0 until durationMs step 5_000L).toList()
            durationMs <= 300_000L -> (0 until durationMs step 15_000L).toList()
            else -> {
                val step = 30_000L
                val allTs = (0 until durationMs step step).toList()
                allTs.take(50)
            }
        }
    }
}
