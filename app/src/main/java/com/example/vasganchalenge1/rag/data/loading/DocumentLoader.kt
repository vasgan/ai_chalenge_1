package com.example.vasganchalenge1.rag.data.loading

import android.content.Context
import android.net.Uri
import com.example.vasganchalenge1.rag.data.local.RagDocumentEntity
import com.example.vasganchalenge1.rag.model.RawDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfTextExtractor: PdfTextExtractor
) {

    suspend fun load(entity: RagDocumentEntity): RawDocument = withContext(Dispatchers.IO) {
        val fileName = entity.displayName
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val source = entity.uriString ?: entity.localPath ?: fileName

        val loaded = when {
            ext == "pdf" -> loadPdf(entity)
            else -> loadPlainText(entity)
        }

        RawDocument(
            source = source,
            filePath = entity.localPath ?: entity.uriString.orEmpty(),
            title = fileName,
            text = loaded.first,
            pageCount = loaded.second
        )
    }

    private fun loadPlainText(entity: RagDocumentEntity): Pair<String, Int?> {
        val text = when {
            !entity.localPath.isNullOrBlank() -> File(entity.localPath).readText(Charsets.UTF_8)
            !entity.uriString.isNullOrBlank() -> {
                val uri = Uri.parse(entity.uriString)
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: ""
            }
            else -> ""
        }
        return text to null
    }

    private fun loadPdf(entity: RagDocumentEntity): Pair<String, Int?> {
        val input = when {
            !entity.localPath.isNullOrBlank() -> File(entity.localPath).inputStream()
            !entity.uriString.isNullOrBlank() -> {
                val uri = Uri.parse(entity.uriString)
                context.contentResolver.openInputStream(uri)
            }
            else -> null
        } ?: return "" to null

        input.use {
            val extracted = pdfTextExtractor.extract(it)
            return extracted.text to extracted.pageCount
        }
    }
}
