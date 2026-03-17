package com.example.vasganchalenge1.rag.domain.retrieval

import com.example.vasganchalenge1.rag.model.RagAnswerSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagChatContextUseCase @Inject constructor(
    private val ragRetriever: RagRetrieverGateway,
    private val ragPromptBuilder: RagPromptBuilder
) {

    suspend fun build(query: String): Result<RagContextResult> {
        return ragRetriever.retrieve(
            query = query,
            topK = RagRetriever.DEFAULT_TOP_K
        ).map { retrieval ->
            when (retrieval) {
                RagRetrievalResult.NoIndex -> RagContextResult.NoIndex
                is RagRetrievalResult.EmptyIndex -> RagContextResult.EmptyIndex(retrieval.manifestId)
                is RagRetrievalResult.Success -> {
                    val payload = ragPromptBuilder.build(retrieval.chunks)
                    RagContextResult.Success(
                        manifestId = retrieval.manifestId,
                        context = payload.contextText,
                        sources = payload.sources
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
        val sources: List<RagAnswerSource>
    ) : RagContextResult

    data object NoIndex : RagContextResult

    data class EmptyIndex(val manifestId: String) : RagContextResult
}
