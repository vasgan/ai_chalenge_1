package com.example.vasganchalenge1.rag.domain.retrieval

import com.example.vasganchalenge1.rag.data.local.RagDao
import com.example.vasganchalenge1.rag.domain.embedding.EmbeddingProviderSelector
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import com.example.vasganchalenge1.rag.model.RetrievedChunk
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class RagRetriever @Inject constructor(
    private val ragDao: RagDao,
    private val embeddingProviderSelector: EmbeddingProviderSelector
) : RagRetrieverGateway {

    override suspend fun retrieve(
        query: String,
        topK: Int
    ): Result<RagRetrievalResult> = runCatching {
        val trimmed = query.trim()
        require(trimmed.isNotBlank()) { "Пустой запрос для retrieval" }

        val manifest = ragDao.getLatestManifest()
            ?: return@runCatching RagRetrievalResult.NoIndex

        val chunks = ragDao.getChunksByManifest(manifest.manifestId)
        if (chunks.isEmpty()) {
            return@runCatching RagRetrievalResult.EmptyIndex(manifest.manifestId)
        }

        val vectorsByChunkId = ragDao.getEmbeddingsByManifest(manifest.manifestId)
            .associate { it.chunkId to parseVector(it.vectorJson) }

        val providerType = EmbeddingProviderType.fromRaw(manifest.embeddingProviderType)
        val queryEmbedding = embeddingProviderSelector.get(providerType).embed(listOf(trimmed)).firstOrNull()
            ?: error("Не удалось построить embedding для запроса")

        val scored = chunks.mapNotNull { chunk ->
            val vector = vectorsByChunkId[chunk.chunkId] ?: return@mapNotNull null
            if (vector.isEmpty()) return@mapNotNull null
            val score = cosineSimilarity(queryEmbedding, vector)
            RetrievedChunk(
                chunkId = chunk.chunkId,
                score = score,
                text = chunk.text,
                source = chunk.source,
                file = chunk.file,
                section = chunk.section
            )
        }.sortedByDescending { it.score }

        RagRetrievalResult.Success(
            manifestId = manifest.manifestId,
            chunks = scored.take(topK.coerceAtLeast(1))
        )
    }

    private fun parseVector(raw: String): FloatArray {
        if (raw.isBlank()) return FloatArray(0)
        val values = raw.split(',')
            .mapNotNull { token -> token.trim().toFloatOrNull() }
        return values.toFloatArray()
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val size = minOf(a.size, b.size)
        if (size == 0) return 0f

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until size) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }

        val denom = sqrt(normA) * sqrt(normB)
        if (denom <= 1e-12) return 0f
        return (dot / denom).toFloat()
    }

    companion object {
        const val DEFAULT_TOP_K = 5
    }
}

interface RagRetrieverGateway {
    suspend fun retrieve(
        query: String,
        topK: Int
    ): Result<RagRetrievalResult>
}

sealed interface RagRetrievalResult {
    data class Success(
        val manifestId: String,
        val chunks: List<RetrievedChunk>
    ) : RagRetrievalResult

    data object NoIndex : RagRetrievalResult

    data class EmptyIndex(val manifestId: String) : RagRetrievalResult
}
