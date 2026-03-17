package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.domain.retrieval.RagChatContextUseCase
import com.example.vasganchalenge1.rag.domain.retrieval.RagContextResult
import com.example.vasganchalenge1.rag.domain.retrieval.RagPromptBuilder
import com.example.vasganchalenge1.rag.domain.retrieval.RagRetrievalResult
import com.example.vasganchalenge1.rag.domain.retrieval.RagRetrieverGateway
import com.example.vasganchalenge1.rag.model.RetrievedChunk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagChatContextUseCaseTest {

    @Test
    fun `returns no index fallback when retriever has no index`() = runBlocking {
        val useCase = RagChatContextUseCase(
            ragRetriever = FakeGateway(Result.success(RagRetrievalResult.NoIndex)),
            ragPromptBuilder = RagPromptBuilder()
        )

        val result = useCase.build("question").getOrThrow()
        assertTrue(result is RagContextResult.NoIndex)
    }

    @Test
    fun `maps retrieved chunks to context and sources`() = runBlocking {
        val useCase = RagChatContextUseCase(
            ragRetriever = FakeGateway(
                Result.success(
                    RagRetrievalResult.Success(
                        manifestId = "m1",
                        chunks = listOf(
                            RetrievedChunk(
                                chunkId = "c1",
                                score = 0.9f,
                                text = "content",
                                source = "src",
                                file = "doc.md",
                                section = "S1"
                            )
                        )
                    )
                )
            ),
            ragPromptBuilder = RagPromptBuilder()
        )

        val result = useCase.build("question").getOrThrow()
        val success = result as RagContextResult.Success
        assertEquals("m1", success.manifestId)
        assertEquals(1, success.sources.size)
        assertTrue(success.context.contains("RAG_CONTEXT"))
    }

    private class FakeGateway(
        private val response: Result<RagRetrievalResult>
    ) : RagRetrieverGateway {
        override suspend fun retrieve(query: String, topK: Int): Result<RagRetrievalResult> = response
    }
}
