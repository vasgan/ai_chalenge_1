package com.example.vasganchalenge1.data.mcp

import android.content.Context
import com.example.mcpserver.SummaryStorageTools
import com.example.mcpserver.TrackingToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSummaryStorageTools @Inject constructor(
    @ApplicationContext private val context: Context
) : SummaryStorageTools {

    override suspend fun saveSummaryToFile(
        title: String,
        summaryText: String,
        rawJson: String
    ): TrackingToolResult = withContext(Dispatchers.IO) {
        if (title.isBlank() || summaryText.isBlank() || rawJson.isBlank()) {
            return@withContext TrackingToolResult(
                text = "title, summaryText and rawJson are required",
                structured = mapOf("error" to "invalid_save_summary_input"),
                isError = true
            )
        }

        runCatching {
            val summariesDir = File(context.filesDir, "mcp_summaries").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val safeTitle = title
                .lowercase(Locale.getDefault())
                .replace(Regex("[^a-z0-9а-яё_-]+", RegexOption.IGNORE_CASE), "_")
                .trim('_')
                .ifBlank { "github_summary" }
            val recordId = "${timestamp}_$safeTitle"
            val file = File(summariesDir, "$recordId.json")

            val payload = buildJsonObject {
                put("recordId", recordId)
                put("title", title)
                put("summaryText", summaryText)
                put("rawJson", rawJson)
                put("createdAt", timestamp)
            }
            file.writeText(payload.toString())

            TrackingToolResult(
                text = "Summary saved: ${file.absolutePath}",
                structured = mapOf(
                    "saved" to true,
                    "filePath" to file.absolutePath,
                    "recordId" to recordId
                )
            )
        }.getOrElse { throwable ->
            TrackingToolResult(
                text = "Failed to save summary: ${throwable.message}",
                structured = mapOf("error" to "save_summary_failed"),
                isError = true
            )
        }
    }
}

