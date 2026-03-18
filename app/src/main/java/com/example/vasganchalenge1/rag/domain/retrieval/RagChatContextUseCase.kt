package com.example.vasganchalenge1.rag.domain.retrieval

import com.example.vasganchalenge1.rag.model.RagAnswerSource
import com.example.vasganchalenge1.rag.model.RagQualityMode
import com.example.vasganchalenge1.rag.model.RagRetrievalConfig
import com.example.vasganchalenge1.data.repositories.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagChatContextUseCase @Inject constructor(
    private val ragRetriever: RagRetrieverGateway,
    private val ragPromptBuilder: RagPromptBuilder,
    private val queryRewriter: RagQueryRewriterGateway,
    private val relevanceFilter: RagRelevanceFilterGateway
) {

    suspend fun build(
        query: String,
        settings: AppSettings,
        config: RagRetrievalConfig
    ): Result<RagContextResult> {
        return runCatching {
            val originalQuery = query.trim()
            val rewriteResult = if (config.mode == RagQualityMode.IMPROVED) {
                queryRewriter.rewrite(settings = settings, userQuestion = originalQuery)
            } else {
                Result.success(originalQuery)
            }
            val retrievalQuery = rewriteResult.getOrDefault(originalQuery).ifBlank { originalQuery }

            val retrieval = ragRetriever.retrieve(
                query = retrievalQuery,
                topK = config.topKBefore
            ).getOrThrow()

            when (retrieval) {
                RagRetrievalResult.NoIndex -> RagContextResult.NoIndex
                is RagRetrievalResult.EmptyIndex -> RagContextResult.EmptyIndex(retrieval.manifestId)
                is RagRetrievalResult.Success -> {
                    val finalChunks = when (config.mode) {
                        RagQualityMode.BASELINE -> retrieval.chunks
                            .take(config.topKAfter.coerceAtLeast(1))
                        RagQualityMode.IMPROVED -> {
                            relevanceFilter.rerankAndFilter(
                                query = retrievalQuery,
                                candidates = retrieval.chunks,
                                config = config
                            ).selected
                        }
                    }
                    val payload = ragPromptBuilder.build(finalChunks)
                    RagContextResult.Success(
                        manifestId = retrieval.manifestId,
                        context = payload.contextText,
                        sources = payload.sources,
                        metadata = RagContextMetadata(
                            mode = config.mode,
                            rewrittenQuery = retrievalQuery.takeIf {
                                config.mode == RagQualityMode.IMPROVED &&
                                    it.isNotBlank() &&
                                    !it.equals(originalQuery, ignoreCase = true)
                            },
                            topKBefore = retrieval.chunks.size,
                            topKAfter = finalChunks.size,
                            similarityThreshold = config.similarityThreshold
                                .takeIf { config.mode == RagQualityMode.IMPROVED }
                        )
                    )
                }
            }
        }
    }
}

sealed interface RagContextResult {
    data class Success(
        val manifestId: String,
        val context: String,
        val sources: List<RagAnswerSource>,
        val metadata: RagContextMetadata
    ) : RagContextResult

    data object NoIndex : RagContextResult

    data class EmptyIndex(val manifestId: String) : RagContextResult
}

data class RagContextMetadata(
    val mode: RagQualityMode,
    val rewrittenQuery: String?,
    val topKBefore: Int,
    val topKAfter: Int,
    val similarityThreshold: Float?
)
