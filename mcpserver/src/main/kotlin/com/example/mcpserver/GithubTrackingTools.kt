package com.example.mcpserver

data class TrackingToolResult(
    val text: String,
    val structured: Any? = null,
    val isError: Boolean = false
)

interface GithubTrackingTools {
    suspend fun scheduleUserMetricTracking(
        username: String,
        intervalSeconds: Int?,
        intervalMinutes: Int?,
        durationHours: Int?,
        metric: String?,
        title: String?
    ): TrackingToolResult

    suspend fun getUserMetricStats(
        trackingId: String?,
        username: String?,
        period: String?,
        includeTimestamps: Boolean?
    ): TrackingToolResult

    suspend fun stopUserMetricTracking(
        trackingId: String? = null
    ): TrackingToolResult
}

object NoopGithubTrackingTools : GithubTrackingTools {
    override suspend fun scheduleUserMetricTracking(
        username: String,
        intervalSeconds: Int?,
        intervalMinutes: Int?,
        durationHours: Int?,
        metric: String?,
        title: String?
    ): TrackingToolResult {
        return TrackingToolResult(
            text = "Tracking tools are not configured in current runtime",
            structured = mapOf("error" to "tracking_tools_unavailable"),
            isError = true
        )
    }

    override suspend fun getUserMetricStats(
        trackingId: String?,
        username: String?,
        period: String?,
        includeTimestamps: Boolean?
    ): TrackingToolResult {
        return TrackingToolResult(
            text = "Tracking tools are not configured in current runtime",
            structured = mapOf("error" to "tracking_tools_unavailable"),
            isError = true
        )
    }

    override suspend fun stopUserMetricTracking(trackingId: String?): TrackingToolResult {
        return TrackingToolResult(
            text = "Tracking tools are not configured in current runtime",
            structured = mapOf("error" to "tracking_tools_unavailable"),
            isError = true
        )
    }
}
