package com.example.vasganchalenge1.rag.domain.chunking

import com.example.vasganchalenge1.rag.model.DocumentChunk
import com.example.vasganchalenge1.rag.model.RawDocument
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StructuredChunker @Inject constructor(
    private val fixedSizeChunker: FixedSizeChunker
) {

    fun chunk(
        documentId: String,
        document: RawDocument,
        maxChunkChars: Int = 1400
    ): List<DocumentChunk> {
        if (document.text.isBlank()) return emptyList()

        val ext = document.title.substringAfterLast('.', "").lowercase()
        val sectioned = when (ext) {
            "md" -> splitMarkdown(document.text)
            "kt", "java" -> splitCode(document.text)
            "pdf" -> splitPdfLike(document.text)
            else -> splitParagraphs(document.text)
        }

        val prepared = mutableListOf<SectionBlock>()
        sectioned.forEach { block ->
            if (block.text.length <= maxChunkChars) {
                prepared += block
            } else {
                prepared += splitOversized(block, maxChunkChars)
            }
        }

        if (prepared.isEmpty()) {
            return fixedSizeChunker.chunk(documentId = documentId, document = document).map {
                it.copy(strategy = STRATEGY)
            }
        }

        return prepared.mapIndexed { index, block ->
            DocumentChunk(
                chunkId = "structured_${UUID.randomUUID()}",
                source = document.source,
                file = document.filePath,
                title = document.title,
                section = block.section,
                strategy = STRATEGY,
                page = block.page,
                startOffset = block.startOffset,
                endOffset = block.endOffset,
                text = block.text,
                documentId = documentId
            )
        }
    }

    private fun splitMarkdown(text: String): List<SectionBlock> {
        val headingRegex = Regex("(?m)^#{1,6}\\s+.+$")
        val matches = headingRegex.findAll(text).toList()
        if (matches.isEmpty()) return splitParagraphs(text)

        val blocks = mutableListOf<SectionBlock>()
        for (index in matches.indices) {
            val current = matches[index]
            val start = current.range.first
            val endExclusive = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            val body = text.substring(start, endExclusive).trim()
            if (body.isNotBlank()) {
                blocks += SectionBlock(
                    section = current.value.removePrefix("#").trim(),
                    text = body,
                    startOffset = start,
                    endOffset = endExclusive,
                    page = null
                )
            }
        }
        return blocks
    }

    private fun splitCode(text: String): List<SectionBlock> {
        val signatureRegex = Regex(
            pattern = "(?m)^(?:class|interface|object|enum|fun|private\\s+fun|public\\s+fun|protected\\s+fun)\\s+[^\\n{]+"
        )
        val matches = signatureRegex.findAll(text).toList()
        if (matches.isEmpty()) return splitParagraphs(text)

        val blocks = mutableListOf<SectionBlock>()
        for (index in matches.indices) {
            val current = matches[index]
            val start = current.range.first
            val endExclusive = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            val body = text.substring(start, endExclusive).trim()
            if (body.isNotBlank()) {
                blocks += SectionBlock(
                    section = current.value.trim(),
                    text = body,
                    startOffset = start,
                    endOffset = endExclusive,
                    page = null
                )
            }
        }
        return blocks
    }

    private fun splitPdfLike(text: String): List<SectionBlock> {
        val pages = text.split('\u000C')
        if (pages.size <= 1) return splitParagraphs(text)

        val blocks = mutableListOf<SectionBlock>()
        var cursor = 0
        pages.forEachIndexed { index, pageText ->
            val trimmed = pageText.trim()
            val start = cursor
            val end = cursor + pageText.length
            if (trimmed.isNotBlank()) {
                blocks += SectionBlock(
                    section = "page_${index + 1}",
                    text = trimmed,
                    startOffset = start,
                    endOffset = end,
                    page = index + 1
                )
            }
            cursor = end + 1
        }
        return blocks
    }

    private fun splitParagraphs(text: String): List<SectionBlock> {
        val regex = Regex("\\n\\s*\\n")
        val blocks = mutableListOf<SectionBlock>()
        var cursor = 0
        regex.findAll(text).forEachIndexed { index, match ->
            val segment = text.substring(cursor, match.range.first).trim()
            if (segment.isNotBlank()) {
                blocks += SectionBlock(
                    section = "section_${index + 1}",
                    text = segment,
                    startOffset = cursor,
                    endOffset = match.range.first,
                    page = null
                )
            }
            cursor = match.range.last + 1
        }
        val tail = text.substring(cursor).trim()
        if (tail.isNotBlank()) {
            blocks += SectionBlock(
                section = "section_${blocks.size + 1}",
                text = tail,
                startOffset = cursor,
                endOffset = text.length,
                page = null
            )
        }
        return blocks
    }

    private fun splitOversized(block: SectionBlock, maxChunkChars: Int): List<SectionBlock> {
        val result = mutableListOf<SectionBlock>()
        var start = 0
        var part = 1
        while (start < block.text.length) {
            val end = (start + maxChunkChars).coerceAtMost(block.text.length)
            val piece = block.text.substring(start, end).trim()
            if (piece.isNotBlank()) {
                result += SectionBlock(
                    section = "${block.section}_part_$part",
                    text = piece,
                    startOffset = block.startOffset?.plus(start),
                    endOffset = block.startOffset?.plus(end),
                    page = block.page
                )
            }
            if (end >= block.text.length) break
            start = end
            part += 1
        }
        return result
    }

    private data class SectionBlock(
        val section: String,
        val text: String,
        val startOffset: Int?,
        val endOffset: Int?,
        val page: Int?
    )

    companion object {
        const val STRATEGY = "structured"
    }
}
