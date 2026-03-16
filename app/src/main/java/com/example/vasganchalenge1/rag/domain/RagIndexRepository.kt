package com.example.vasganchalenge1.rag.domain

import android.net.Uri
import androidx.room.withTransaction
import com.example.vasganchalenge1.rag.data.importing.DocumentImportManager
import com.example.vasganchalenge1.rag.data.loading.DocumentLoader
import com.example.vasganchalenge1.rag.data.local.ChunkEmbeddingEntity
import com.example.vasganchalenge1.rag.data.local.ChunkingComparisonReportEntity
import com.example.vasganchalenge1.rag.data.local.ExportedRagResultEntity
import com.example.vasganchalenge1.rag.data.local.IndexedChunkEntity
import com.example.vasganchalenge1.rag.data.local.RagDao
import com.example.vasganchalenge1.rag.data.local.RagDatabase
import com.example.vasganchalenge1.rag.data.local.RagDocumentEntity
import com.example.vasganchalenge1.rag.data.local.RagIndexManifestEntity
import com.example.vasganchalenge1.rag.domain.chunking.FixedSizeChunker
import com.example.vasganchalenge1.rag.domain.chunking.StructuredChunker
import com.example.vasganchalenge1.rag.domain.embedding.EmbeddingProvider
import com.example.vasganchalenge1.rag.model.ChunkingComparisonReport
import com.example.vasganchalenge1.rag.model.ChunkingStats
import com.example.vasganchalenge1.rag.model.DocumentChunk
import com.example.vasganchalenge1.rag.model.RagDocumentFile
import com.example.vasganchalenge1.rag.model.RagDocumentStatus
import com.example.vasganchalenge1.rag.model.RagExportedResult
import com.example.vasganchalenge1.rag.model.RagIndexingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagIndexRepository @Inject constructor(
    private val ragDatabase: RagDatabase,
    private val ragDao: RagDao,
    private val documentImportManager: DocumentImportManager,
    private val documentLoader: DocumentLoader,
    private val fixedSizeChunker: FixedSizeChunker,
    private val structuredChunker: StructuredChunker,
    private val embeddingProvider: EmbeddingProvider,
    private val comparisonService: ChunkingComparisonService,
    private val resultExporter: RagResultExporter
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeDocuments(): Flow<List<RagDocumentFile>> {
        return ragDao.observeDocuments().map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun observeExports(): Flow<List<RagExportedResult>> {
        return ragDao.observeExports().map { entities ->
            entities.map {
                RagExportedResult(
                    exportId = it.exportId,
                    manifestId = it.manifestId,
                    fileName = it.fileName,
                    localPath = it.localPath,
                    createdAt = it.createdAt
                )
            }
        }
    }

    fun observeLatestReport(): Flow<ChunkingComparisonReport?> {
        return ragDao.observeLatestComparisonReport().map { entity ->
            entity?.let { mapReportEntity(it) }
        }
    }

    suspend fun importDocuments(uris: List<Uri>): Result<Unit> {
        return documentImportManager.importDocuments(uris)
    }

    suspend fun deleteDocument(documentId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            ragDatabase.withTransaction {
                val document = ragDao.getDocument(documentId)
                    ?: error("Файл не найден")

                if (!document.localPath.isNullOrBlank()) {
                    runCatching { File(document.localPath).delete() }
                }

                ragDao.deleteChunksByDocumentId(documentId)
                ragDao.deleteDocument(documentId)
            }
        }
    }

    suspend fun deleteExport(exportId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val export = ragDao.getExportById(exportId)
                ?: error("Экспортированный файл не найден")

            runCatching {
                File(export.localPath).takeIf { it.exists() }?.delete()
            }
            ragDao.deleteExport(exportId)
        }
    }

    suspend fun buildIndex(): Result<RagIndexingResult> = runCatching {
        withContext(Dispatchers.IO) {
            val sourceEntities = ragDao.getAllDocuments()
                .filter { it.status != RagDocumentStatus.ERROR.name }

            require(sourceEntities.isNotEmpty()) { "Нет импортированных файлов для индексации" }

            val loaded = mutableListOf<Pair<RagDocumentEntity, com.example.vasganchalenge1.rag.model.RawDocument>>()
            sourceEntities.forEach { entity ->
                runCatching { documentLoader.load(entity) }
                    .onSuccess { raw ->
                        if (raw.text.isNotBlank()) {
                            loaded += entity to raw
                        } else {
                            ragDao.updateDocumentStatus(
                                documentId = entity.id,
                                status = RagDocumentStatus.ERROR.name,
                                lastError = "Пустой текст документа",
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                    }
                    .onFailure { throwable ->
                        ragDao.updateDocumentStatus(
                            documentId = entity.id,
                            status = RagDocumentStatus.ERROR.name,
                            lastError = throwable.message,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
            }

            require(loaded.isNotEmpty()) { "Не удалось извлечь текст из выбранных файлов" }

            val fixedChunks = loaded.flatMap { (entity, raw) ->
                fixedSizeChunker.chunk(documentId = entity.id, document = raw)
            }
            val structuredChunks = loaded.flatMap { (entity, raw) ->
                structuredChunker.chunk(documentId = entity.id, document = raw)
            }

            val allChunks = fixedChunks + structuredChunks
            require(allChunks.isNotEmpty()) { "Не удалось построить чанки" }

            val embeddings = embedInBatches(allChunks.map { it.text })
            require(embeddings.size == allChunks.size) { "Размер embeddings не совпадает с числом чанков" }

            val report = comparisonService.buildReport(
                fixedChunks = fixedChunks,
                structuredChunks = structuredChunks
            )
            val builtAt = report.builtAt
            val manifestId = UUID.randomUUID().toString()

            val exportResult = resultExporter.export(
                manifestId = manifestId,
                documents = loaded.map { (entity, _) -> entity.toModel(statusOverride = RagDocumentStatus.INDEXED) },
                report = report
            ).getOrThrow()
            val vectorsExportResult = resultExporter.exportVectors(
                manifestId = manifestId,
                chunks = allChunks,
                embeddings = embeddings
            ).getOrThrow()

            ragDatabase.withTransaction {
                ragDao.insertManifest(
                    RagIndexManifestEntity(
                        manifestId = manifestId,
                        builtAt = builtAt,
                        documentsCount = loaded.size,
                        fixedChunksCount = fixedChunks.size,
                        structuredChunksCount = structuredChunks.size,
                        fixedAverageChunkLength = report.fixedStats.averageChunkLength,
                        structuredAverageChunkLength = report.structuredStats.averageChunkLength,
                        exportedJsonPath = exportResult.localPath,
                        embeddingEngine = embeddingProvider.engineName
                    )
                )

                ragDao.insertChunks(allChunks.map { chunk ->
                    IndexedChunkEntity(
                        chunkId = chunk.chunkId,
                        manifestId = manifestId,
                        documentId = chunk.documentId,
                        strategy = chunk.strategy,
                        source = chunk.source,
                        file = chunk.file,
                        title = chunk.title,
                        section = chunk.section,
                        page = chunk.page,
                        startOffset = chunk.startOffset,
                        endOffset = chunk.endOffset,
                        text = chunk.text
                    )
                })

                ragDao.insertEmbeddings(
                    allChunks.zip(embeddings).map { (chunk, vector) ->
                        ChunkEmbeddingEntity(
                            manifestId = manifestId,
                            chunkId = chunk.chunkId,
                            vectorJson = vector.joinToString(",") { value ->
                                value.toString()
                            }
                        )
                    }
                )

                val fixedStatsJson = statsToJson(report.fixedStats)
                val structuredStatsJson = statsToJson(report.structuredStats)
                ragDao.insertComparisonReport(
                    ChunkingComparisonReportEntity(
                        manifestId = manifestId,
                        builtAt = builtAt,
                        fixedStatsJson = fixedStatsJson,
                        structuredStatsJson = structuredStatsJson,
                        exportedJsonPath = exportResult.localPath
                    )
                )

                ragDao.insertExport(
                    ExportedRagResultEntity(
                        exportId = exportResult.exportId,
                        manifestId = manifestId,
                        fileName = exportResult.fileName,
                        localPath = exportResult.localPath,
                        createdAt = exportResult.createdAt
                    )
                )
                ragDao.insertExport(
                    ExportedRagResultEntity(
                        exportId = vectorsExportResult.exportId,
                        manifestId = manifestId,
                        fileName = vectorsExportResult.fileName,
                        localPath = vectorsExportResult.localPath,
                        createdAt = vectorsExportResult.createdAt
                    )
                )

                loaded.forEach { (entity, _) ->
                    ragDao.updateDocumentStatus(
                        documentId = entity.id,
                        status = RagDocumentStatus.INDEXED.name,
                        lastError = null,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }

            RagIndexingResult(
                manifestId = manifestId,
                documentsCount = loaded.size,
                fixedChunksCount = fixedChunks.size,
                structuredChunksCount = structuredChunks.size,
                fixedAverageChunkLength = report.fixedStats.averageChunkLength,
                structuredAverageChunkLength = report.structuredStats.averageChunkLength,
                builtAt = builtAt,
                exportedJsonPath = exportResult.localPath,
                vectorsExportPath = vectorsExportResult.localPath,
                embeddingEngine = embeddingProvider.engineName
            )
        }
    }

    private suspend fun embedInBatches(
        texts: List<String>,
        batchSize: Int = 16
    ): List<FloatArray> {
        val result = mutableListOf<FloatArray>()
        texts.chunked(batchSize).forEach { batch ->
            result += embeddingProvider.embed(batch)
        }
        return result
    }

    private fun RagDocumentEntity.toModel(statusOverride: RagDocumentStatus? = null): RagDocumentFile {
        return RagDocumentFile(
            id = id,
            displayName = displayName,
            mimeType = mimeType,
            localPath = localPath,
            uriString = uriString,
            status = statusOverride ?: runCatching { RagDocumentStatus.valueOf(status) }
                .getOrDefault(RagDocumentStatus.IMPORTED),
            sizeBytes = sizeBytes,
            lastError = lastError,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun statsToJson(stats: ChunkingStats): String {
        return buildString {
            append("{")
            append("\"strategy\":\"").append(stats.strategy).append("\",")
            append("\"documentsCount\":").append(stats.documentsCount).append(',')
            append("\"chunksCount\":").append(stats.chunksCount).append(',')
            append("\"averageChunkLength\":").append(stats.averageChunkLength).append(',')
            append("\"maxChunkLength\":").append(stats.maxChunkLength).append(',')
            append("\"perDocumentChunkCount\":{")
            append(stats.perDocumentChunkCount.entries.joinToString(",") { (k, v) ->
                "\"$k\":$v"
            })
            append("}")
            append("}")
        }
    }

    private fun mapReportEntity(entity: ChunkingComparisonReportEntity): ChunkingComparisonReport {
        val fixed = parseStats(entity.fixedStatsJson)
        val structured = parseStats(entity.structuredStatsJson)
        return ChunkingComparisonReport(
            fixedStats = fixed,
            structuredStats = structured,
            builtAt = entity.builtAt,
            exportedJsonPath = entity.exportedJsonPath
        )
    }

    private fun parseStats(raw: String): ChunkingStats {
        val root = json.parseToJsonElement(raw).jsonObject
        val perDoc = mutableMapOf<String, Int>()
        root["perDocumentChunkCount"]?.jsonObject?.forEach { (key, value) ->
            perDoc[key] = value.jsonPrimitive.intOrNull ?: 0
        }
        return ChunkingStats(
            strategy = root["strategy"]?.jsonPrimitive?.content.orEmpty(),
            documentsCount = root["documentsCount"]?.jsonPrimitive?.intOrNull ?: 0,
            chunksCount = root["chunksCount"]?.jsonPrimitive?.intOrNull ?: 0,
            averageChunkLength = root["averageChunkLength"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            maxChunkLength = root["maxChunkLength"]?.jsonPrimitive?.intOrNull ?: 0,
            perDocumentChunkCount = perDoc
        )
    }
}
