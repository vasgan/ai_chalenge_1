package com.example.vasganchalenge1.rag.model

enum class RagQualityMode {
    BASELINE,
    IMPROVED;

    companion object {
        fun fromRaw(raw: String?): RagQualityMode {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: IMPROVED
        }
    }
}

data class RagRetrievalConfig(
    val mode: RagQualityMode = RagQualityMode.IMPROVED,
    val topKBefore: Int = 8,
    val topKAfter: Int = 4,
    val similarityThreshold: Float = 0.55f
)
