package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.rag.domain.retrieval.RagRelevanceFilter
import com.example.vasganchalenge1.rag.model.RagQualityMode
import com.example.vasganchalenge1.rag.model.RagRetrievalConfig
import com.example.vasganchalenge1.rag.model.RetrievedChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagRelevanceFilterTest {

    private val filter = RagRelevanceFilter()

    @Test
    fun `filters by threshold and applies topKAfter`() {
        val result = filter.rerankAndFilter(
            query = "github user repositories",
            candidates = listOf(
                chunk("c1", score = 0.90f, text = "GitHub user repositories and stars"),
                chunk("c2", score = 0.75f, text = "Repositories overview"),
                chunk("c3", score = 0.20f, text = "unrelated weather report")
            ),
            config = RagRetrievalConfig(
                mode = RagQualityMode.IMPROVED,
                topKBefore = 8,
                topKAfter = 2,
                similarityThreshold = 0.55f
            )
        )

        assertEquals(3, result.beforeCount)
        assertTrue(result.afterCount <= 2)
        assertTrue(result.selected.none { it.chunkId == "c3" })
    }

    @Test
    fun `falls back to best chunks when nothing above threshold`() {
        val result = filter.rerankAndFilter(
            query = "github",
            candidates = listOf(
                chunk("c1", score = 0.10f, text = "aaa"),
                chunk("c2", score = 0.08f, text = "bbb"),
                chunk("c3", score = 0.06f, text = "ccc")
            ),
            config = RagRetrievalConfig(
                mode = RagQualityMode.IMPROVED,
                topKBefore = 8,
                topKAfter = 2,
                similarityThreshold = 0.99f
            )
        )

        assertTrue(result.fallbackUsed)
        assertEquals(2, result.selected.size)
    }

    private fun chunk(id: String, score: Float, text: String): RetrievedChunk {
        return RetrievedChunk(
            chunkId = id,
            score = score,
            text = text,
            source = "src",
            file = "doc.md",
            section = "section"
        )
    }
}
