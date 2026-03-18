package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.rag.domain.retrieval.RagChatContextUseCase
import com.example.vasganchalenge1.rag.domain.retrieval.RagContextResult
import com.example.vasganchalenge1.rag.domain.retrieval.RagPromptBuilder
import com.example.vasganchalenge1.rag.domain.retrieval.RagQueryRewriterGateway
import com.example.vasganchalenge1.rag.domain.retrieval.RagRelevanceFilterGateway
import com.example.vasganchalenge1.rag.domain.retrieval.RagRelevanceFilterResult
import com.example.vasganchalenge1.rag.domain.retrieval.RagRetrievalResult
import com.example.vasganchalenge1.rag.domain.retrieval.RagRetrieverGateway
import com.example.vasganchalenge1.rag.model.RagQualityMode
import com.example.vasganchalenge1.rag.model.RagRetrievalConfig
import com.example.vasganchalenge1.rag.model.RetrievedChunk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagChatContextUseCaseTest {

    @Test
    fun `returns no index fallback when retriever has no index`() = runBlocking {
        val useCase = buildUseCase(
            retriever = FakeRetriever(Result.success(RagRetrievalResult.NoIndex))
        )

        val result = useCase.build(
            query = "question",
            settings = AppSettings(),
            config = RagRetrievalConfig()
        ).getOrThrow()

        assertTrue(result is RagContextResult.NoIndex)
    }

    @Test
    fun `baseline mode uses no rewrite and no filter`() = runBlocking {
        val rewriter = FakeRewriter(Result.success("rewritten"))
        val filter = FakeFilter(
            RagRelevanceFilterResult(
                selected = listOf(chunk("filtered", 0.1f)),
                beforeCount = 1,
                afterCount = 1,
                usedThreshold = 0.55f,
                fallbackUsed = false
            )
        )
        val retriever = FakeRetriever(
            Result.success(
                RagRetrievalResult.Success(
                    manifestId = "m1",
                    chunks = listOf(chunk("c1", 0.9f), chunk("c2", 0.8f))
                )
            )
        )
        val useCase = buildUseCase(retriever = retriever, rewriter = rewriter, filter = filter)

        val result = useCase.build(
            query = "original question",
            settings = AppSettings(),
            config = RagRetrievalConfig(
                mode = RagQualityMode.BASELINE,
                topKBefore = 8,
                topKAfter = 1,
                similarityThreshold = 0.55f
            )
        ).getOrThrow() as RagContextResult.Success

        assertEquals(0, rewriter.calls)
        assertEquals(0, filter.calls)
        assertEquals(1, result.metadata.topKAfter)
        assertNull(result.metadata.rewrittenQuery)
        assertEquals("original question", retriever.lastQuery)
    }

    @Test
    fun `improved mode uses rewrite and filter`() = runBlocking {
        val rewriter = FakeRewriter(Result.success("rewritten query"))
        val filter = FakeFilter(
            RagRelevanceFilterResult(
                selected = listOf(chunk("c2", 0.95f)),
                beforeCount = 2,
                afterCount = 1,
                usedThreshold = 0.55f,
                fallbackUsed = false
            )
        )
        val retriever = FakeRetriever(
            Result.success(
                RagRetrievalResult.Success(
                    manifestId = "m1",
                    chunks = listOf(chunk("c1", 0.6f), chunk("c2", 0.7f))
                )
            )
        )
        val useCase = buildUseCase(retriever = retriever, rewriter = rewriter, filter = filter)

        val result = useCase.build(
            query = "original question",
            settings = AppSettings(),
            config = RagRetrievalConfig(mode = RagQualityMode.IMPROVED)
        ).getOrThrow() as RagContextResult.Success

        assertEquals(1, rewriter.calls)
        assertEquals(1, filter.calls)
        assertEquals("rewritten query", retriever.lastQuery)
        assertEquals("rewritten query", result.metadata.rewrittenQuery)
        assertEquals(2, result.metadata.topKBefore)
        assertEquals(1, result.metadata.topKAfter)
        assertEquals(0.55f, result.metadata.similarityThreshold)
    }

    @Test
    fun `query rewrite fallback uses original query`() = runBlocking {
        val rewriter = FakeRewriter(Result.failure(IllegalStateException("rewrite failed")))
        val retriever = FakeRetriever(
            Result.success(
                RagRetrievalResult.Success(
                    manifestId = "m1",
                    chunks = listOf(chunk("c1", 0.8f))
                )
            )
        )
        val useCase = buildUseCase(retriever = retriever, rewriter = rewriter)

        val result = useCase.build(
            query = "original",
            settings = AppSettings(),
            config = RagRetrievalConfig(mode = RagQualityMode.IMPROVED)
        ).getOrThrow() as RagContextResult.Success

        assertEquals("original", retriever.lastQuery)
        assertNull(result.metadata.rewrittenQuery)
    }

    @Test
    fun `topKBefore is passed to retriever and topKAfter reflected in metadata`() = runBlocking {
        val retriever = FakeRetriever(
            Result.success(
                RagRetrievalResult.Success(
                    manifestId = "m1",
                    chunks = listOf(chunk("c1", 0.8f), chunk("c2", 0.7f), chunk("c3", 0.6f))
                )
            )
        )
        val useCase = buildUseCase(retriever = retriever)

        val result = useCase.build(
            query = "q",
            settings = AppSettings(),
            config = RagRetrievalConfig(
                mode = RagQualityMode.BASELINE,
                topKBefore = 8,
                topKAfter = 2,
                similarityThreshold = 0.55f
            )
        ).getOrThrow() as RagContextResult.Success

        assertEquals(8, retriever.lastTopK)
        assertEquals(2, result.metadata.topKAfter)
    }

    private fun buildUseCase(
        retriever: FakeRetriever,
        rewriter: FakeRewriter = FakeRewriter(Result.success("rewritten")),
        filter: FakeFilter = FakeFilter(
            RagRelevanceFilterResult(
                selected = listOf(chunk("c1", 0.9f)),
                beforeCount = 1,
                afterCount = 1,
                usedThreshold = 0.55f,
                fallbackUsed = false
            )
        )
    ): RagChatContextUseCase {
        return RagChatContextUseCase(
            ragRetriever = retriever,
            ragPromptBuilder = RagPromptBuilder(),
            queryRewriter = rewriter,
            relevanceFilter = filter
        )
    }

    private fun chunk(id: String, score: Float): RetrievedChunk {
        return RetrievedChunk(
            chunkId = id,
            score = score,
            text = "content for $id",
            source = "src",
            file = "doc.md",
            section = "Section"
        )
    }

    private class FakeRetriever(
        private val response: Result<RagRetrievalResult>
    ) : RagRetrieverGateway {
        var lastQuery: String = ""
            private set
        var lastTopK: Int = 0
            private set

        override suspend fun retrieve(query: String, topK: Int): Result<RagRetrievalResult> {
            lastQuery = query
            lastTopK = topK
            return response
        }
    }

    private class FakeRewriter(
        private val response: Result<String>
    ) : RagQueryRewriterGateway {
        var calls: Int = 0
            private set

        override suspend fun rewrite(settings: AppSettings, userQuestion: String): Result<String> {
            calls++
            return response
        }
    }

    private class FakeFilter(
        private val response: RagRelevanceFilterResult
    ) : RagRelevanceFilterGateway {
        var calls: Int = 0
            private set

        override fun rerankAndFilter(
            query: String,
            candidates: List<RetrievedChunk>,
            config: RagRetrievalConfig
        ): RagRelevanceFilterResult {
            calls++
            return response.copy(beforeCount = candidates.size)
        }
    }
}
