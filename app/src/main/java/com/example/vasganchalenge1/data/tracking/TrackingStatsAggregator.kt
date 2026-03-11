package com.example.vasganchalenge1.data.tracking

data class TrackingPoint(
    val timestamp: Long,
    val value: Double
)

data class AggregatedTrackingStats(
    val currentValue: Double,
    val minValue: Double,
    val maxValue: Double,
    val delta: Double,
    val firstValue: Double,
    val lastValue: Double,
    val samplesCount: Int,
    val startedAt: Long,
    val lastCollectedAt: Long
)

fun aggregateTrackingPoints(points: List<TrackingPoint>): AggregatedTrackingStats? {
    if (points.isEmpty()) return null
    val sorted = points.sortedBy { it.timestamp }
    val values = sorted.map { it.value }
    val first = sorted.first()
    val last = sorted.last()
    return AggregatedTrackingStats(
        currentValue = last.value,
        minValue = values.minOrNull() ?: last.value,
        maxValue = values.maxOrNull() ?: last.value,
        delta = last.value - first.value,
        firstValue = first.value,
        lastValue = last.value,
        samplesCount = sorted.size,
        startedAt = first.timestamp,
        lastCollectedAt = last.timestamp
    )
}

fun parsePeriodToMillis(period: String?): Long {
    val normalized = period?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank() || normalized == "day" || normalized == "24h") {
        return 24L * 60L * 60L * 1000L
    }

    val hourMatch = Regex("^(\\d{1,3})h$").find(normalized)
    if (hourMatch != null) {
        val hours = hourMatch.groupValues[1].toLongOrNull()?.coerceAtLeast(1L) ?: 24L
        return hours * 60L * 60L * 1000L
    }

    val dayMatch = Regex("^(\\d{1,3})d$").find(normalized)
    if (dayMatch != null) {
        val days = dayMatch.groupValues[1].toLongOrNull()?.coerceAtLeast(1L) ?: 1L
        return days * 24L * 60L * 60L * 1000L
    }

    return 24L * 60L * 60L * 1000L
}

fun isTrackingExpired(startedAt: Long, durationHours: Int, now: Long): Boolean {
    if (durationHours <= 0) return false
    val durationMs = durationHours.toLong() * 60L * 60L * 1000L
    return now >= startedAt + durationMs
}
