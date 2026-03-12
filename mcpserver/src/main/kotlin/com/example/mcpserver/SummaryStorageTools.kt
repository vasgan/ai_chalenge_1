package com.example.mcpserver

interface SummaryStorageTools {
    suspend fun saveSummaryToFile(
        title: String,
        summaryText: String,
        rawJson: String
    ): TrackingToolResult
}

object NoopSummaryStorageTools : SummaryStorageTools {
    override suspend fun saveSummaryToFile(
        title: String,
        summaryText: String,
        rawJson: String
    ): TrackingToolResult {
        return TrackingToolResult(
            text = "Summary storage tools are not configured in current runtime",
            structured = mapOf("error" to "summary_storage_unavailable"),
            isError = true
        )
    }
}

