package com.example.vasganchalenge1.rag.domain

import android.content.Context
import com.example.vasganchalenge1.rag.model.ChunkingComparisonReport
import com.example.vasganchalenge1.rag.model.DocumentChunk
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import com.example.vasganchalenge1.rag.model.RagDocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagResultExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun export(
        manifestId: String,
        documents: List<RagDocumentFile>,
        report: ChunkingComparisonReport,
        embeddingProviderType: EmbeddingProviderType,
        embeddingModel: String
    ): Result<ExportResult> = runCatching {
        withContext(Dispatchers.IO) {
            val exportDir = File(context.filesDir, "rag/exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val fileName = "rag_result_${System.currentTimeMillis()}.txt"
            val file = File(exportDir, fileName)

            val payload = buildJsonObject {
                put("manifestId", manifestId)
                put("builtAt", report.builtAt)
                put("embeddingProviderType", embeddingProviderType.name)
                put("embeddingModel", embeddingModel)
                put("documents", buildJsonArray {
                    documents.forEach { doc ->
                        add(buildJsonObject {
                            put("id", doc.id)
                            put("displayName", doc.displayName)
                            put("mimeType", doc.mimeType ?: "")
                            put("localPath", doc.localPath ?: "")
                            put("uriString", doc.uriString ?: "")
                            put("status", doc.status.name)
                            put("sizeBytes", doc.sizeBytes ?: -1)
                        })
                    }
                })
                put("fixedStats", statsToJson(report.fixedStats))
                put("structuredStats", statsToJson(report.structuredStats))
                put("comparison", buildJsonObject {
                    put("fixedChunks", report.fixedStats.chunksCount)
                    put("structuredChunks", report.structuredStats.chunksCount)
                    put(
                        "avgLengthDelta",
                        report.structuredStats.averageChunkLength - report.fixedStats.averageChunkLength
                    )
                })
            }

            file.writeText(json.encodeToString(JsonObject.serializer(), payload), Charsets.UTF_8)

            ExportResult(
                exportId = UUID.randomUUID().toString(),
                fileName = fileName,
                localPath = file.absolutePath,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun exportVectors(
        manifestId: String,
        chunks: List<DocumentChunk>,
        embeddings: List<FloatArray>,
        embeddingProviderType: EmbeddingProviderType,
        embeddingModel: String
    ): Result<ExportResult> = runCatching {
        withContext(Dispatchers.IO) {
            require(chunks.size == embeddings.size) {
                "Chunks and embeddings size mismatch"
            }

            val exportDir = File(context.filesDir, "rag/exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val fileName = "rag_vectors_${System.currentTimeMillis()}.txt"
            val file = File(exportDir, fileName)

            val payload = buildJsonObject {
                put("manifestId", manifestId)
                put("generatedAt", System.currentTimeMillis())
                put("embeddingProviderType", embeddingProviderType.name)
                put("embeddingModel", embeddingModel)
                put("chunksCount", chunks.size)
                put("vectors", buildJsonArray {
                    chunks.zip(embeddings).forEach { (chunk, vector) ->
                        add(buildJsonObject {
                            put("chunk_id", chunk.chunkId)
                            put("source", chunk.source)
                            put("file", chunk.file)
                            put("title", chunk.title ?: "")
                            put("section", chunk.section ?: "")
                            put("strategy", chunk.strategy)
                            put("page", chunk.page ?: -1)
                            put("startOffset", chunk.startOffset ?: -1)
                            put("endOffset", chunk.endOffset ?: -1)
                            put("textLength", chunk.text.length)
                            put("textPreview", chunk.text.take(220))
                            put("embeddingDimension", vector.size)
                            put("embedding", buildJsonArray {
                                vector.forEach { add(it) }
                            })
                        })
                    }
                })
            }

            file.writeText(json.encodeToString(JsonObject.serializer(), payload), Charsets.UTF_8)
            ExportResult(
                exportId = UUID.randomUUID().toString(),
                fileName = fileName,
                localPath = file.absolutePath,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    private fun statsToJson(stats: com.example.vasganchalenge1.rag.model.ChunkingStats): JsonObject {
        return buildJsonObject {
            put("strategy", stats.strategy)
            put("documentsCount", stats.documentsCount)
            put("chunksCount", stats.chunksCount)
            put("averageChunkLength", stats.averageChunkLength)
            put("maxChunkLength", stats.maxChunkLength)
            put("perDocumentChunkCount", buildJsonObject {
                stats.perDocumentChunkCount.forEach { (key, value) ->
                    put(key, value)
                }
            })
        }
    }

    data class ExportResult(
        val exportId: String,
        val fileName: String,
        val localPath: String,
        val createdAt: Long
    )
}
