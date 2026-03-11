package com.example.vasganchalenge1.data.tracking

object TrackingMetric {
    const val TOTAL_STARS = "total_stars"
    const val PUBLIC_REPOS = "public_repos"

    val supported = setOf(TOTAL_STARS, PUBLIC_REPOS)
}

object TrackingStatus {
    const val ACTIVE = "ACTIVE"
    const val STOPPED = "STOPPED"
    const val COMPLETED = "COMPLETED"
    const val ERROR = "ERROR"
}

internal const val DEFAULT_INTERVAL_SECONDS = 60 * 60
internal const val DEFAULT_DURATION_HOURS = 24
internal const val DEFAULT_METRIC = TrackingMetric.TOTAL_STARS
internal const val DEFAULT_STATS_PERIOD = "day"
internal const val WORK_NAME_PREFIX = "github_tracking_"
internal const val BOOTSTRAP_WORK_PREFIX = "github_tracking_bootstrap_"
