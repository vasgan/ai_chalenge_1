package com.example.vasganchalenge1.data.tracking

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracking_jobs",
    indices = [
        Index(value = ["username", "metric", "status"])
    ]
)
data class TrackingJobEntity(
    @PrimaryKey val trackingId: String,
    val username: String,
    val metric: String,
    val intervalSeconds: Int,
    val durationHours: Int,
    val title: String?,
    val status: String,
    val createdAt: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val lastCollectedAt: Long?,
    val workName: String
)

@Entity(
    tableName = "tracking_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = TrackingJobEntity::class,
            parentColumns = ["trackingId"],
            childColumns = ["trackingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trackingId"]),
        Index(value = ["collectedAt"])
    ]
)
data class TrackingSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackingId: String,
    val collectedAt: Long,
    val metricValue: Double,
    val rawJson: String,
    val summaryText: String?
)
