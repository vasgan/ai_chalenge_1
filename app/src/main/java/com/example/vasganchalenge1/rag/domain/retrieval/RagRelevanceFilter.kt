package com.example.vasganchalenge1.rag.domain.retrieval

import com.example.vasganchalenge1.rag.model.RagRetrievalConfig
import com.example.vasganchalenge1.rag.model.RetrievedChunk
import javax.inject.Inject
import javax.inject.Singleton

interface RagRelevanceFilterGateway {
    fun rerankAndFilter(
        query: String,
        candidates: List<RetrievedChunk>,
        config: RagRetrievalConfig
    ): RagRelevanceFilterResult
}

@Singleton
class RagRelevanceFilter @Inject constructor() : RagRelevanceFilterGateway {

    override fun rerankAndFilter(
        query: String,
        candidates: List<RetrievedChunk>,
        config: RagRetrievalConfig
    ): RagRelevanceFilterResult {
        if (candidates.isEmpty()) {
            return RagRelevanceFilterResult(
                selected = emptyList(),
                beforeCount = 0,
                afterCount = 0,
                usedThreshold = config.similarityThreshold,
                fallbackUsed = false
            )
        }

        val queryTokens = tokenize(query)
        val scored = candidates.map { chunk ->
            val overlap = keywordOverlap(queryTokens, tokenize(chunk.text))
            val sectionBoost = computeSectionBoost(queryTokens, chunk)
            val baseScore = (0.8f * chunk.score) + (0.15f * overlap) + (0.05f * sectionBoost)
            RelevanceCandidate(chunk = chunk, baseScore = baseScore)
        }.sortedByDescending { it.baseScore }

        val selected = mutableListOf<RetrievedChunk>()
        val sectionSeen = mutableMapOf<String, Int>()

        scored.forEach { candidate ->
            if (selected.size >= config.topKAfter) return@forEach
            val sectionKey = (candidate.chunk.file + "::" + (candidate.chunk.section ?: "")).lowercase()
            val seenCount = sectionSeen[sectionKey] ?: 0
            val duplicatePenalty = 0.03f * seenCount
            val shortPenalty = if (candidate.chunk.text.length < 80) 0.05f else 0f
            val finalScore = candidate.baseScore - duplicatePenalty - shortPenalty

            if (finalScore >= config.similarityThreshold) {
                selected += candidate.chunk.copy(score = finalScore)
                sectionSeen[sectionKey] = seenCount + 1
            }
        }

        if (selected.isNotEmpty()) {
            return RagRelevanceFilterResult(
                selected = selected,
                beforeCount = candidates.size,
                afterCount = selected.size,
                usedThreshold = config.similarityThreshold,
                fallbackUsed = false
            )
        }

        val fallback = scored.take(FALLBACK_CHUNKS_COUNT.coerceAtMost(config.topKAfter.coerceAtLeast(1)))
            .map { it.chunk }

        return RagRelevanceFilterResult(
            selected = fallback,
            beforeCount = candidates.size,
            afterCount = fallback.size,
            usedThreshold = config.similarityThreshold,
            fallbackUsed = true
        )
    }

    private fun computeSectionBoost(queryTokens: Set<String>, chunk: RetrievedChunk): Float {
        if (queryTokens.isEmpty()) return 0f
        val sectionTokens = tokenize((chunk.section ?: "") + " " + chunk.file)
        if (sectionTokens.isEmpty()) return 0f
        return keywordOverlap(queryTokens, sectionTokens)
    }

    private fun keywordOverlap(queryTokens: Set<String>, chunkTokens: Set<String>): Float {
        if (queryTokens.isEmpty() || chunkTokens.isEmpty()) return 0f
        val intersection = queryTokens.intersect(chunkTokens).size
        return (intersection.toFloat() / queryTokens.size.toFloat()).coerceIn(0f, 1f)
    }

    private fun tokenize(value: String): Set<String> {
        return value.lowercase()
            .split(Regex("[^\\p{L}\\p{N}_]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .toSet()
    }

    private data class RelevanceCandidate(
        val chunk: RetrievedChunk,
        val baseScore: Float
    )

    private companion object {
        const val FALLBACK_CHUNKS_COUNT = 2
    }
}

data class RagRelevanceFilterResult(
    val selected: List<RetrievedChunk>,
    val beforeCount: Int,
    val afterCount: Int,
    val usedThreshold: Float,
    val fallbackUsed: Boolean
)
