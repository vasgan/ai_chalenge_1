package com.example.vasganchalenge1.rag.domain.retrieval

import com.example.vasganchalenge1.rag.model.RagAnswerSource
import com.example.vasganchalenge1.rag.model.RetrievedChunk
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagPromptBuilder @Inject constructor() {

    fun build(chunks: List<RetrievedChunk>, maxChars: Int = DEFAULT_MAX_CHARS): RagPromptPayload {
        if (chunks.isEmpty()) {
            return RagPromptPayload(contextText = "", sources = emptyList())
        }

        val contextBuilder = StringBuilder()
        val selected = mutableListOf<RetrievedChunk>()

        chunks.forEachIndexed { index, chunk ->
            val block = buildString {
                append("[source ").append(index + 1).append("]\n")
                append("file: ").append(chunk.file).append('\n')
                append("section: ").append(chunk.section ?: "-").append('\n')
                append("chunk_id: ").append(chunk.chunkId).append('\n')
                append("text: ").append(chunk.text.trim()).append("\n\n")
            }

            if (contextBuilder.length + block.length > maxChars && selected.isNotEmpty()) {
                return@forEachIndexed
            }
            if (contextBuilder.length + block.length <= maxChars) {
                contextBuilder.append(block)
                selected += chunk
            }
        }

        val context = if (contextBuilder.isEmpty()) {
            ""
        } else {
            "[RAG_CONTEXT]\n" + contextBuilder.toString().trimEnd() + "\n[/RAG_CONTEXT]"
        }

        val sources = selected.map { chunk ->
            RagAnswerSource(
                chunkId = chunk.chunkId,
                file = chunk.file,
                section = chunk.section
            )
        }

        return RagPromptPayload(
            contextText = context,
            sources = sources
        )
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 5000
    }
}

data class RagPromptPayload(
    val contextText: String,
    val sources: List<RagAnswerSource>
)
