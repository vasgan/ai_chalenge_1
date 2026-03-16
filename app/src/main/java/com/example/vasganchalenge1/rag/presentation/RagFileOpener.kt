package com.example.vasganchalenge1.rag.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object RagFileOpener {

    fun openImportedDocument(
        context: Context,
        localPath: String?,
        uriString: String?,
        mimeType: String?
    ): Result<Unit> {
        return runCatching {
            val uri = resolveUri(context, localPath, uriString)
                ?: error("Не удалось определить URI файла")
            open(context, uri, mimeType)
        }
    }

    fun openExportedJson(context: Context, localPath: String): Result<Unit> {
        return runCatching {
            val file = File(localPath)
            require(file.exists()) { "Файл не найден: $localPath" }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            open(context, uri, "text/plain")
        }
    }

    private fun resolveUri(context: Context, localPath: String?, uriString: String?): Uri? {
        if (!localPath.isNullOrBlank()) {
            val file = File(localPath)
            if (file.exists()) {
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        }

        if (!uriString.isNullOrBlank()) {
            return Uri.parse(uriString)
        }

        return null
    }

    private fun open(context: Context, uri: Uri, mimeType: String?) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Открыть файл").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chooser)
        } catch (notFound: ActivityNotFoundException) {
            throw IllegalStateException("Нет приложения для открытия этого файла")
        }
    }
}
