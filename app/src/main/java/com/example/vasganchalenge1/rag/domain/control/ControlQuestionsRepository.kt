package com.example.vasganchalenge1.rag.domain.control

import com.example.vasganchalenge1.rag.data.local.ControlQuestionEntity
import com.example.vasganchalenge1.rag.data.local.RagDao
import com.example.vasganchalenge1.rag.model.ControlQuestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ControlQuestionsRepository @Inject constructor(
    private val ragDao: RagDao
) {

    fun observeLatestIndexId(): Flow<String?> {
        return ragDao.observeLatestManifest().map { it?.manifestId }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeQuestionsForLatestIndex(): Flow<List<ControlQuestion>> {
        return observeLatestIndexId().flatMapLatest { indexId ->
            if (indexId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                ragDao.observeControlQuestions(indexId).map { entities ->
                    entities.map { it.toModel() }
                }
            }
        }
    }

    suspend fun getLatestIndexId(): String? {
        return ragDao.getLatestManifest()?.manifestId
    }

    suspend fun replaceQuestions(indexId: String, questions: List<ControlQuestion>) {
        val now = System.currentTimeMillis()
        val normalized = questions.map { question ->
            val normalizedId = question.id.ifBlank { UUID.randomUUID().toString() }
            question.copy(
                id = normalizedId,
                indexId = indexId,
                question = question.question.trim(),
                expectation = question.expectation.trim(),
                expectedSources = question.expectedSources
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            )
        }.filter { it.question.isNotBlank() }

        ragDao.deleteControlQuestionsByIndexId(indexId)
        if (normalized.isEmpty()) return

        ragDao.upsertControlQuestions(
            normalized.map { question ->
                ControlQuestionEntity(
                    id = question.id,
                    indexId = indexId,
                    question = question.question,
                    expectation = question.expectation,
                    expectedSourcesJson = serializeSources(question.expectedSources),
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
    }

    suspend fun deleteQuestion(questionId: String) {
        ragDao.deleteControlQuestion(questionId)
    }

    suspend fun buildKnowledgeSummary(): Result<ControlQuestionsKnowledgeSummary> = runCatching {
        val manifest = ragDao.getLatestManifest() ?: error("Нет построенного индекса")
        val chunks = ragDao.getChunksByManifest(manifest.manifestId)
        if (chunks.isEmpty()) {
            error("Индекс пуст")
        }

        val documentIds = chunks.map { it.documentId }.distinct()
        val docs = if (documentIds.isEmpty()) {
            emptyList()
        } else {
            ragDao.getDocumentsByIds(documentIds)
        }

        val documentsSummary = docs.joinToString(separator = "\n") { doc ->
            "- ${doc.displayName} (${doc.mimeType ?: "unknown"})"
        }.ifBlank {
            chunks.map { it.file }.distinct().joinToString(separator = "\n") { "- $it" }
        }

        val sectionsSummary = chunks.asSequence()
            .mapNotNull { it.section?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(30)
            .joinToString(separator = "\n") { "- $it" }
            .ifBlank { "- sections not detected" }

        val keyTopics = chunks.asSequence()
            .flatMap { chunk ->
                chunk.text.lowercase().split(Regex("[^\\p{L}\\p{N}_]+"))
                    .asSequence()
            }
            .filter { token -> token.length >= 5 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(25)
            .joinToString(separator = ", ") { it.key }

        ControlQuestionsKnowledgeSummary(
            indexId = manifest.manifestId,
            documentsSummary = documentsSummary,
            sectionsSummary = sectionsSummary,
            topicsSummary = keyTopics.ifBlank { "no dominant topics detected" }
        )
    }

    private fun ControlQuestionEntity.toModel(): ControlQuestion {
        return ControlQuestion(
            id = id,
            indexId = indexId,
            question = question,
            expectation = expectation,
            expectedSources = deserializeSources(expectedSourcesJson)
        )
    }

    private fun serializeSources(value: List<String>): String {
        return value.joinToString(separator = SOURCES_SEPARATOR)
    }

    private fun deserializeSources(raw: String): List<String> {
        return raw.split(SOURCES_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private companion object {
        const val SOURCES_SEPARATOR = "\n"
    }
}

data class ControlQuestionsKnowledgeSummary(
    val indexId: String,
    val documentsSummary: String,
    val sectionsSummary: String,
    val topicsSummary: String
)
