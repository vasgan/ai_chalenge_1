package com.example.vasganchalenge1.data.tracking

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrackingJobEntity::class, TrackingSnapshotEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun trackingJobDao(): TrackingJobDao
    abstract fun trackingSnapshotDao(): TrackingSnapshotDao

    companion object {
        @Volatile
        private var instance: TrackingDatabase? = null

        fun getInstance(context: Context): TrackingDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackingDatabase::class.java,
                    "tracking_stats.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
