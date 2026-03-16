package com.example.vasganchalenge1.rag.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rag_documents")
data class RagDocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val mimeType: String?,
    val localPath: String?,
    val uriString: String?,
    val status: String,
    val sizeBytes: Long?,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "rag_index_manifest",
    indices = [
        Index(value = ["builtAt"])
    ]
)
data class RagIndexManifestEntity(
    @PrimaryKey val manifestId: String,
    val builtAt: Long,
    val documentsCount: Int,
    val fixedChunksCount: Int,
    val structuredChunksCount: Int,
    val fixedAverageChunkLength: Double,
    val structuredAverageChunkLength: Double,
    val exportedJsonPath: String?,
    val embeddingEngine: String
)

@Entity(
    tableName = "rag_indexed_chunks",
    indices = [
        Index(value = ["manifestId"]),
        Index(value = ["documentId"]),
        Index(value = ["strategy"])
    ]
)
data class IndexedChunkEntity(
    @PrimaryKey val chunkId: String,
    val manifestId: String,
    val documentId: String,
    val strategy: String,
    val source: String,
    val file: String,
    val title: String?,
    val section: String?,
    val page: Int?,
    val startOffset: Int?,
    val endOffset: Int?,
    val text: String
)

@Entity(
    tableName = "rag_chunk_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = IndexedChunkEntity::class,
            parentColumns = ["chunkId"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["manifestId"]),
        Index(value = ["chunkId"])
    ]
)
data class ChunkEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val manifestId: String,
    val chunkId: String,
    val vectorJson: String
)

@Entity(tableName = "rag_comparison_reports")
data class ChunkingComparisonReportEntity(
    @PrimaryKey val manifestId: String,
    val builtAt: Long,
    val fixedStatsJson: String,
    val structuredStatsJson: String,
    val exportedJsonPath: String?
)

@Entity(
    tableName = "rag_exported_results",
    indices = [
        Index(value = ["manifestId"]),
        Index(value = ["createdAt"])
    ]
)
data class ExportedRagResultEntity(
    @PrimaryKey val exportId: String,
    val manifestId: String,
    val fileName: String,
    val localPath: String,
    val createdAt: Long
)
