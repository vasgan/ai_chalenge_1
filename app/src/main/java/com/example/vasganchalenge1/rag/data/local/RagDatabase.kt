package com.example.vasganchalenge1.rag.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RagDocumentEntity::class,
        RagIndexManifestEntity::class,
        IndexedChunkEntity::class,
        ChunkEmbeddingEntity::class,
        ChunkingComparisonReportEntity::class,
        ExportedRagResultEntity::class,
        ControlQuestionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RagDatabase : RoomDatabase() {
    abstract fun ragDao(): RagDao

    companion object {
        @Volatile
        private var instance: RagDatabase? = null

        fun getInstance(context: Context): RagDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RagDatabase::class.java,
                    "rag_index.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
