package com.example.vasganchalenge1.data.tracking

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.mcpserver.GithubApiClient
import com.example.mcpserver.TrackingToolResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

class GithubTrackingService private constructor(
    private val appContext: Context,
    private val database: TrackingDatabase,
    private val githubApiClient: GithubApiClient,
    moshi: Moshi
) {
    private val tag = "GithubTrackingService"
    private val jobDao = database.trackingJobDao()
    private val snapshotDao = database.trackingSnapshotDao()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )

    suspend fun scheduleTracking(
        username: String,
        intervalSeconds: Int?,
        intervalMinutes: Int?,
        durationHours: Int?,
        metric: String?,
        title: String?
    ): TrackingToolResult = withContext(Dispatchers.IO) {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank()) {
            return@withContext TrackingToolResult(
                text = "username обязателен",
                structured = mapOf("error" to "username_is_required"),
                isError = true
            )
        }

        val requestedIntervalSeconds = when {
            (intervalSeconds ?: 0) > 0 -> intervalSeconds ?: DEFAULT_INTERVAL_SECONDS
            (intervalMinutes ?: 0) > 0 -> (intervalMinutes ?: 0) * 60
            intervalSeconds == null && intervalMinutes == null -> DEFAULT_INTERVAL_SECONDS
            else -> 0
        }
        if (requestedIntervalSeconds <= 0) {
            return@withContext TrackingToolResult(
                text = "Интервал должен быть > 0 (intervalSeconds или intervalMinutes)",
                structured = mapOf("error" to "invalid_interval"),
                isError = true
            )
        }

        val effectiveDuration = durationHours ?: DEFAULT_DURATION_HOURS
        if (effectiveDuration <= 0) {
            return@withContext TrackingToolResult(
                text = "durationHours должен быть > 0",
                structured = mapOf("error" to "invalid_duration"),
                isError = true
            )
        }

        val normalizedMetric = metric?.trim()?.lowercase().orEmpty().ifBlank { DEFAULT_METRIC }
        if (normalizedMetric !in TrackingMetric.supported) {
            return@withContext TrackingToolResult(
                text = "Неподдерживаемая метрика: $normalizedMetric",
                structured = mapOf(
                    "error" to "unsupported_metric",
                    "supportedMetrics" to TrackingMetric.supported.toList()
                ),
                isError = true
            )
        }

        val activeDuplicate = jobDao.findAnyActive()
        if (activeDuplicate != null) {
            return@withContext TrackingToolResult(
                text = "Сбор уже запущен. Сначала остановите текущий трекинг.",
                structured = mapOf(
                    "username" to activeDuplicate.username,
                    "intervalSeconds" to activeDuplicate.intervalSeconds,
                    "intervalMinutes" to activeDuplicate.intervalSeconds / 60.0,
                    "durationHours" to activeDuplicate.durationHours,
                    "metric" to activeDuplicate.metric,
                    "status" to activeDuplicate.status,
                    "alreadyExists" to true
                ),
                isError = true
            )
        }

        val now = System.currentTimeMillis()
        val trackingId = UUID.randomUUID().toString()
        val workName = "$WORK_NAME_PREFIX$trackingId"
        val normalizedTitle = title?.trim()?.ifBlank { null }

        val entity = TrackingJobEntity(
            trackingId = trackingId,
            username = normalizedUsername,
            metric = normalizedMetric,
            intervalSeconds = requestedIntervalSeconds,
            durationHours = effectiveDuration,
            title = normalizedTitle,
            status = TrackingStatus.ACTIVE,
            createdAt = now,
            startedAt = now,
            endedAt = null,
            lastCollectedAt = null,
            workName = workName
        )
        jobDao.upsert(entity)

        val scheduleResult = runCatching {
            scheduleWorkNow(workName = workName, trackingId = trackingId)
        }

        if (scheduleResult.isFailure) {
            jobDao.updateStatus(
                trackingId = trackingId,
                status = TrackingStatus.ERROR,
                endedAt = System.currentTimeMillis()
            )
            return@withContext TrackingToolResult(
                text = "Не удалось запланировать WorkManager job: ${scheduleResult.exceptionOrNull()?.message}",
                structured = mapOf("error" to "workmanager_schedule_failed"),
                isError = true
            )
        }

        val note = "Используется OneTimeWorkRequest с автопланированием следующего запуска."
        TrackingToolResult(
            text = "Сбор запущен. Интервал=${requestedIntervalSeconds}с. $note",
            structured = mapOf(
                "username" to normalizedUsername,
                "intervalSeconds" to requestedIntervalSeconds,
                "intervalMinutes" to requestedIntervalSeconds / 60.0,
                "durationHours" to effectiveDuration,
                "metric" to normalizedMetric,
                "status" to TrackingStatus.ACTIVE,
                "note" to note
            )
        )
    }

    suspend fun getStats(
        trackingId: String?,
        username: String?,
        period: String?,
        includeTimestamps: Boolean?
    ): TrackingToolResult = withContext(Dispatchers.IO) {
        val resolvedJob = resolveTrackingJob(trackingId = trackingId, username = username)
            ?: return@withContext TrackingToolResult(
                text = "Tracking job не найден",
                structured = mapOf("error" to "tracking_not_found"),
                isError = true
            )

        val periodLabel = period?.trim().orEmpty().ifBlank { DEFAULT_STATS_PERIOD }
        val periodMs = parsePeriodToMillis(period)
        val fromTs = System.currentTimeMillis() - periodMs

        val snapshots = snapshotDao.findByTrackingSince(resolvedJob.trackingId, fromTs)
        if (snapshots.isEmpty()) {
            return@withContext TrackingToolResult(
                text = "Данных пока нет. Сбор только что начат или измерений за период нет.",
                structured = mapOf(
                    "username" to resolvedJob.username,
                    "metric" to resolvedJob.metric,
                    "period" to periodLabel,
                    "samplesCount" to 0,
                    "startedAt" to resolvedJob.startedAt,
                    "lastCollectedAt" to resolvedJob.lastCollectedAt
                )
            )
        }

        val points = snapshots.map { TrackingPoint(timestamp = it.collectedAt, value = it.metricValue) }
        val aggregated = aggregateTrackingPoints(points)
            ?: return@withContext TrackingToolResult(
                text = "Данных пока нет",
                structured = mapOf("samplesCount" to 0)
            )

        val structuredPoints = if (includeTimestamps != false) {
            points.map { point -> mapOf("timestamp" to point.timestamp, "value" to point.value) }
        } else {
            emptyList()
        }

        val summaryText = "За период $periodLabel собрано ${aggregated.samplesCount} измерений. " +
            "Значение метрики изменилось с ${formatNumber(aggregated.firstValue)} до ${formatNumber(aggregated.lastValue)}. " +
            "Изменение: ${formatSigned(aggregated.delta)}."

        TrackingToolResult(
            text = summaryText,
            structured = mapOf(
                "username" to resolvedJob.username,
                "metric" to resolvedJob.metric,
                "period" to periodLabel,
                "samplesCount" to aggregated.samplesCount,
                "startedAt" to resolvedJob.startedAt,
                "lastCollectedAt" to aggregated.lastCollectedAt,
                "currentValue" to aggregated.currentValue,
                "minValue" to aggregated.minValue,
                "maxValue" to aggregated.maxValue,
                "delta" to aggregated.delta,
                "points" to structuredPoints,
                "summaryText" to summaryText
            )
        )
    }

    suspend fun stopTracking(trackingId: String?): TrackingToolResult = withContext(Dispatchers.IO) {
        val job = resolveTrackingJob(
            trackingId = trackingId,
            username = null
        ) ?: return@withContext TrackingToolResult(
            text = "Трекинг не найден",
            structured = mapOf("error" to "tracking_not_found"),
            isError = true
        )

        val samplesCount = snapshotDao.countByTracking(job.trackingId)
        val now = System.currentTimeMillis()

        if (job.status != TrackingStatus.ACTIVE) {
            return@withContext TrackingToolResult(
                text = "Tracking уже не активен (status=${job.status})",
                structured = mapOf(
                    "stopped" to false,
                    "status" to job.status,
                    "stoppedAt" to job.endedAt,
                    "finalSamplesCount" to samplesCount
                )
            )
        }

        WorkManager.getInstance(appContext).cancelUniqueWork(job.workName)
        jobDao.updateStatus(job.trackingId, TrackingStatus.STOPPED, now)

        TrackingToolResult(
            text = "Сбор остановлен.",
            structured = mapOf(
                "username" to job.username,
                "metric" to job.metric,
                "stopped" to true,
                "stoppedAt" to now,
                "finalSamplesCount" to samplesCount
            )
        )
    }

    suspend fun collectSnapshotForWorker(trackingId: String): WorkerCollectionResult = withContext(Dispatchers.IO) {
        Log.i(tag, "Collect cycle started. trackingId=$trackingId")
        val job = jobDao.findById(trackingId) ?: return@withContext WorkerCollectionResult.FAILURE
        if (job.status != TrackingStatus.ACTIVE) {
            Log.i(tag, "Collect cycle skipped: job is not ACTIVE. trackingId=$trackingId status=${job.status}")
            return@withContext WorkerCollectionResult.SUCCESS
        }

        val now = System.currentTimeMillis()
        if (isTrackingExpired(job.startedAt, job.durationHours, now)) {
            WorkManager.getInstance(appContext).cancelUniqueWork(job.workName)
            jobDao.updateStatus(job.trackingId, TrackingStatus.COMPLETED, now)
            Log.i(tag, "Collect cycle completed and stopped by duration. trackingId=$trackingId")
            return@withContext WorkerCollectionResult.SUCCESS
        }

        val metricResult = runCatching { collectMetric(job.username, job.metric) }
            .getOrElse {
                // Временная ошибка сети/GitHub: попробуем повторить.
                Log.e(tag, "Collect cycle failed. trackingId=$trackingId", it)
                return@withContext WorkerCollectionResult.RETRY
            }

        val snapshot = TrackingSnapshotEntity(
            trackingId = job.trackingId,
            collectedAt = now,
            metricValue = metricResult.first,
            rawJson = mapAdapter.toJson(metricResult.second),
            summaryText = "${job.metric}: ${formatNumber(metricResult.first)}"
        )
        snapshotDao.insert(snapshot)
        jobDao.updateLastCollectedAt(job.trackingId, now)
        Log.i(
            tag,
            "Snapshot saved. trackingId=$trackingId metric=${job.metric} value=${formatNumber(metricResult.first)} at=$now"
        )

        val stillActive = jobDao.findById(job.trackingId)?.status == TrackingStatus.ACTIVE
        if (stillActive) {
            val scheduled = runCatching {
                scheduleNextRun(
                    workName = job.workName,
                    trackingId = job.trackingId,
                    delaySeconds = job.intervalSeconds
                )
            }.isSuccess
            if (!scheduled) {
                Log.e(tag, "Failed to schedule next run. trackingId=$trackingId")
                return@withContext WorkerCollectionResult.RETRY
            }
            Log.i(tag, "Next run scheduled. trackingId=$trackingId delaySeconds=${job.intervalSeconds}")
        } else {
            Log.i(tag, "Next run not scheduled: job is no longer ACTIVE. trackingId=$trackingId")
        }

        Log.i(tag, "Collect cycle finished successfully. trackingId=$trackingId")
        WorkerCollectionResult.SUCCESS
    }

    private suspend fun resolveTrackingJob(
        trackingId: String?,
        username: String?
    ): TrackingJobEntity? {
        val byId = trackingId?.trim().orEmpty()
        if (byId.isNotBlank()) return jobDao.findById(byId)

        val byUsername = username?.trim().orEmpty()
        if (byUsername.isNotBlank()) return jobDao.findLatestByUsername(byUsername)

        return jobDao.findAnyActive() ?: jobDao.findLatestAny()
    }

    private suspend fun collectMetric(username: String, metric: String): Pair<Double, Map<String, Any?>> {
        return when (metric) {
            TrackingMetric.TOTAL_STARS -> {
                val repos = githubApiClient.listUserRepos(username).getOrThrow()
                val totalStars = repos.sumOf { repo ->
                    (repo["stargazers_count"] as? Number)?.toDouble()
                        ?: repo["stargazers_count"]?.toString()?.toDoubleOrNull()
                        ?: 0.0
                }
                totalStars to mapOf(
                    "username" to username,
                    "metric" to TrackingMetric.TOTAL_STARS,
                    "reposCount" to repos.size,
                    "totalStars" to totalStars
                )
            }

            TrackingMetric.PUBLIC_REPOS -> {
                val user = githubApiClient.getUser(username).getOrThrow()
                val publicRepos = (user["public_repos"] as? Number)?.toDouble()
                    ?: user["public_repos"]?.toString()?.toDoubleOrNull()
                    ?: error("GitHub field public_repos is not available")
                publicRepos to mapOf(
                    "username" to username,
                    "metric" to TrackingMetric.PUBLIC_REPOS,
                    "publicRepos" to publicRepos
                )
            }

            else -> error("Unsupported metric: $metric")
        }
    }

    private fun scheduleWorkNow(workName: String, trackingId: String) {
        val bootstrapWork = OneTimeWorkRequestBuilder<GithubTrackingWorker>()
            .setInputData(workDataOf(GithubTrackingWorker.KEY_TRACKING_ID to trackingId))
            .addTag(workName)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            bootstrapWork
        )
    }

    private fun scheduleNextRun(workName: String, trackingId: String, delaySeconds: Int) {
        val safeDelay = delaySeconds.coerceAtLeast(1)
        val request = OneTimeWorkRequestBuilder<GithubTrackingWorker>()
            .setInputData(workDataOf(GithubTrackingWorker.KEY_TRACKING_ID to trackingId))
            .setInitialDelay(safeDelay.toLong(), TimeUnit.SECONDS)
            .addTag(workName)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private fun formatSigned(value: Double): String {
        return if (value >= 0) "+${formatNumber(value)}" else formatNumber(value)
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value)
        }
    }

    companion object {
        @Volatile
        private var instance: GithubTrackingService? = null

        fun getInstance(context: Context): GithubTrackingService {
            return instance ?: synchronized(this) {
                instance ?: GithubTrackingService(
                    appContext = context.applicationContext,
                    database = TrackingDatabase.getInstance(context.applicationContext),
                    githubApiClient = GithubApiClient(),
                    moshi = Moshi.Builder().build()
                ).also { instance = it }
            }
        }
    }
}

enum class WorkerCollectionResult {
    SUCCESS,
    RETRY,
    FAILURE
}
