package com.example.vasganchalenge1.data.tracking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackingJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: TrackingJobEntity)

    @Query("SELECT * FROM tracking_jobs WHERE trackingId = :trackingId LIMIT 1")
    suspend fun findById(trackingId: String): TrackingJobEntity?

    @Query(
        "SELECT * FROM tracking_jobs " +
            "WHERE username = :username AND metric = :metric AND status = :activeStatus " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun findActiveByUsernameMetric(
        username: String,
        metric: String,
        activeStatus: String = TrackingStatus.ACTIVE
    ): TrackingJobEntity?

    @Query(
        "SELECT * FROM tracking_jobs " +
            "WHERE status = :activeStatus " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun findAnyActive(activeStatus: String = TrackingStatus.ACTIVE): TrackingJobEntity?

    @Query("SELECT * FROM tracking_jobs WHERE username = :username ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLatestByUsername(username: String): TrackingJobEntity?

    @Query("SELECT * FROM tracking_jobs ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLatestAny(): TrackingJobEntity?

    @Query("UPDATE tracking_jobs SET status = :status, endedAt = :endedAt WHERE trackingId = :trackingId")
    suspend fun updateStatus(trackingId: String, status: String, endedAt: Long?)

    @Query("UPDATE tracking_jobs SET lastCollectedAt = :lastCollectedAt WHERE trackingId = :trackingId")
    suspend fun updateLastCollectedAt(trackingId: String, lastCollectedAt: Long)
}

@Dao
interface TrackingSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: TrackingSnapshotEntity)

    @Query(
        "SELECT * FROM tracking_snapshots " +
            "WHERE trackingId = :trackingId AND collectedAt >= :fromTs " +
            "ORDER BY collectedAt ASC"
    )
    suspend fun findByTrackingSince(trackingId: String, fromTs: Long): List<TrackingSnapshotEntity>

    @Query("SELECT COUNT(*) FROM tracking_snapshots WHERE trackingId = :trackingId")
    suspend fun countByTracking(trackingId: String): Int

    @Query("SELECT * FROM tracking_snapshots WHERE trackingId = :trackingId ORDER BY collectedAt DESC LIMIT 1")
    suspend fun findLatest(trackingId: String): TrackingSnapshotEntity?
}
