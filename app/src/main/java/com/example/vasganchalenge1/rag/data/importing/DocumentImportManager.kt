package com.example.vasganchalenge1.rag.data.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.vasganchalenge1.rag.data.local.RagDao
import com.example.vasganchalenge1.rag.data.local.RagDocumentEntity
import com.example.vasganchalenge1.rag.model.RagDocumentStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ragDao: RagDao
) {
    private val supportedExtensions = setOf("md", "txt", "pdf", "kt", "java")

    suspend fun importDocuments(uris: List<Uri>): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val imported = uris.mapNotNull { uri ->
                runCatching {
                    val meta = resolveMeta(uri)
                    val ext = meta.displayName.substringAfterLast('.', "").lowercase()
                    if (ext !in supportedExtensions) {
                        return@runCatching null
                    }

                    val destination = createDestinationFile(meta.displayName)
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Не удалось открыть файл: ${meta.displayName}" }
                        destination.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    RagDocumentEntity(
                        id = UUID.randomUUID().toString(),
                        displayName = meta.displayName,
                        mimeType = meta.mimeType,
                        localPath = destination.absolutePath,
                        uriString = uri.toString(),
                        status = RagDocumentStatus.IMPORTED.name,
                        sizeBytes = meta.sizeBytes,
                        lastError = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }.getOrElse { error ->
                    RagDocumentEntity(
                        id = UUID.randomUUID().toString(),
                        displayName = uri.lastPathSegment ?: "unknown",
                        mimeType = null,
                        localPath = null,
                        uriString = uri.toString(),
                        status = RagDocumentStatus.ERROR.name,
                        sizeBytes = null,
                        lastError = error.message,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }

            if (imported.isNotEmpty()) {
                ragDao.upsertDocuments(imported)
            }
        }
    }

    private fun createDestinationFile(displayName: String): File {
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(context.filesDir, "rag/imported")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${System.currentTimeMillis()}_$safeName")
    }

    private fun resolveMeta(uri: Uri): ImportMeta {
        var displayName = uri.lastPathSegment ?: "document_${System.currentTimeMillis()}"
        var sizeBytes: Long? = null

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        val mimeType = context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())

        return ImportMeta(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes
        )
    }

    private data class ImportMeta(
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long?
    )
}
