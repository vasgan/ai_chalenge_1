package com.example.vasganchalenge1.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingStatsAggregatorTest {

    @Test
    fun `aggregateTrackingPoints computes min max delta and current`() {
        val points = listOf(
            TrackingPoint(timestamp = 1000L, value = 10.0),
            TrackingPoint(timestamp = 2000L, value = 13.0),
            TrackingPoint(timestamp = 3000L, value = 11.0)
        )

        val stats = aggregateTrackingPoints(points)
        assertNotNull(stats)
        stats!!
        assertEquals(3, stats.samplesCount)
        assertEquals(10.0, stats.minValue, 0.0001)
        assertEquals(13.0, stats.maxValue, 0.0001)
        assertEquals(1.0, stats.delta, 0.0001)
        assertEquals(11.0, stats.currentValue, 0.0001)
    }

    @Test
    fun `aggregateTrackingPoints returns null for empty list`() {
        assertNull(aggregateTrackingPoints(emptyList()))
    }

    @Test
    fun `parsePeriodToMillis supports day and hours`() {
        assertEquals(24L * 60L * 60L * 1000L, parsePeriodToMillis("day"))
        assertEquals(12L * 60L * 60L * 1000L, parsePeriodToMillis("12h"))
        assertEquals(48L * 60L * 60L * 1000L, parsePeriodToMillis("2d"))
    }

    @Test
    fun `parsePeriodToMillis falls back to day for invalid value`() {
        assertEquals(24L * 60L * 60L * 1000L, parsePeriodToMillis("invalid"))
    }

    @Test
    fun `isTrackingExpired respects duration hours`() {
        val startedAt = 1_000L
        val now = startedAt + 5L * 60L * 60L * 1000L

        assertFalse(isTrackingExpired(startedAt, durationHours = 8, now = now))
        assertTrue(isTrackingExpired(startedAt, durationHours = 4, now = now))
    }
}
