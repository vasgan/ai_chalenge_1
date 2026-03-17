package com.example.vasganchalenge1.rag.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<RagDocumentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: RagDocumentEntity)

    @Query("SELECT * FROM rag_documents ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<RagDocumentEntity>>

    @Query("SELECT * FROM rag_documents ORDER BY createdAt ASC")
    suspend fun getAllDocuments(): List<RagDocumentEntity>

    @Query("SELECT * FROM rag_documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocument(documentId: String): RagDocumentEntity?

    @Query("SELECT * FROM rag_documents WHERE id IN (:documentIds)")
    suspend fun getDocumentsByIds(documentIds: List<String>): List<RagDocumentEntity>

    @Query("DELETE FROM rag_documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: String)

    @Query("DELETE FROM rag_indexed_chunks WHERE documentId = :documentId")
    suspend fun deleteChunksByDocumentId(documentId: String)

    @Query(
        "UPDATE rag_documents SET status = :status, lastError = :lastError, updatedAt = :updatedAt WHERE id = :documentId"
    )
    suspend fun updateDocumentStatus(documentId: String, status: String, lastError: String?, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManifest(manifest: RagIndexManifestEntity)

    @Query("SELECT * FROM rag_index_manifest ORDER BY builtAt DESC LIMIT 1")
    fun observeLatestManifest(): Flow<RagIndexManifestEntity?>

    @Query("SELECT * FROM rag_index_manifest ORDER BY builtAt DESC LIMIT 1")
    suspend fun getLatestManifest(): RagIndexManifestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<IndexedChunkEntity>)

    @Query("SELECT * FROM rag_indexed_chunks WHERE manifestId = :manifestId")
    suspend fun getChunksByManifest(manifestId: String): List<IndexedChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<ChunkEmbeddingEntity>)

    @Query("SELECT * FROM rag_chunk_embeddings WHERE manifestId = :manifestId")
    suspend fun getEmbeddingsByManifest(manifestId: String): List<ChunkEmbeddingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComparisonReport(report: ChunkingComparisonReportEntity)

    @Query("SELECT * FROM rag_comparison_reports ORDER BY builtAt DESC LIMIT 1")
    fun observeLatestComparisonReport(): Flow<ChunkingComparisonReportEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExport(exported: ExportedRagResultEntity)

    @Query("SELECT * FROM rag_exported_results ORDER BY createdAt DESC")
    fun observeExports(): Flow<List<ExportedRagResultEntity>>

    @Query("SELECT * FROM rag_exported_results WHERE exportId = :exportId LIMIT 1")
    suspend fun getExportById(exportId: String): ExportedRagResultEntity?

    @Query("DELETE FROM rag_exported_results WHERE exportId = :exportId")
    suspend fun deleteExport(exportId: String)

    @Query("UPDATE rag_index_manifest SET exportedJsonPath = :exportedJsonPath WHERE manifestId = :manifestId")
    suspend fun updateManifestExportPath(manifestId: String, exportedJsonPath: String)

    @Query("SELECT * FROM rag_control_questions WHERE indexId = :indexId ORDER BY updatedAt DESC")
    fun observeControlQuestions(indexId: String): Flow<List<ControlQuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertControlQuestions(questions: List<ControlQuestionEntity>)

    @Query("DELETE FROM rag_control_questions WHERE id = :questionId")
    suspend fun deleteControlQuestion(questionId: String)

    @Query("DELETE FROM rag_control_questions WHERE indexId = :indexId")
    suspend fun deleteControlQuestionsByIndexId(indexId: String)
}
