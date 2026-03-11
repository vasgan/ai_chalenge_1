package com.example.vasganchalenge1.data.tracking

import android.content.Context
import com.example.mcpserver.GithubTrackingTools
import com.example.mcpserver.TrackingToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidGithubTrackingTools @Inject constructor(
    @ApplicationContext private val context: Context
) : GithubTrackingTools {

    private val service: GithubTrackingService
        get() = GithubTrackingService.getInstance(context)

    override suspend fun scheduleUserMetricTracking(
        username: String,
        intervalSeconds: Int?,
        intervalMinutes: Int?,
        durationHours: Int?,
        metric: String?,
        title: String?
    ): TrackingToolResult {
        return service.scheduleTracking(
            username = username,
            intervalSeconds = intervalSeconds,
            intervalMinutes = intervalMinutes,
            durationHours = durationHours,
            metric = metric,
            title = title
        )
    }

    override suspend fun getUserMetricStats(
        trackingId: String?,
        username: String?,
        period: String?,
        includeTimestamps: Boolean?
    ): TrackingToolResult {
        return service.getStats(
            trackingId = trackingId,
            username = username,
            period = period,
            includeTimestamps = includeTimestamps
        )
    }

    override suspend fun stopUserMetricTracking(trackingId: String?): TrackingToolResult {
        return service.stopTracking(trackingId)
    }
}
