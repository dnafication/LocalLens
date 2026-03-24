package com.locallens.app.data.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoFrameExtractorTest {

    private val extractor = VideoFrameExtractor()

    @Test
    fun `zero duration returns single timestamp at 0`() {
        val timestamps = extractor.computeTimestamps(0)
        assertEquals(listOf(0L), timestamps)
    }

    @Test
    fun `short video under 10s extracts 2 frames`() {
        val timestamps = extractor.computeTimestamps(8000) // 8 seconds
        assertEquals(2, timestamps.size)
        assertEquals(0L, timestamps[0])
        assertEquals(4000L, timestamps[1]) // 50% of 8000
    }

    @Test
    fun `10s-60s video extracts every 5 seconds`() {
        val timestamps = extractor.computeTimestamps(30000) // 30 seconds
        assertEquals(listOf(0L, 5000L, 10000L, 15000L, 20000L, 25000L), timestamps)
    }

    @Test
    fun `60s-300s video extracts every 15 seconds`() {
        val timestamps = extractor.computeTimestamps(120000) // 2 minutes
        assertEquals(listOf(0L, 15000L, 30000L, 45000L, 60000L, 75000L, 90000L, 105000L), timestamps)
    }

    @Test
    fun `long video over 300s extracts every 30s capped at 50 frames`() {
        val timestamps = extractor.computeTimestamps(3600000) // 1 hour
        assertTrue(timestamps.size <= 50)
        assertEquals(0L, timestamps[0])
        assertEquals(30000L, timestamps[1])
    }

    @Test
    fun `very short video 1s extracts 2 frames`() {
        val timestamps = extractor.computeTimestamps(1000)
        assertEquals(2, timestamps.size)
        assertEquals(0L, timestamps[0])
        assertEquals(500L, timestamps[1])
    }
}
