package com.example.vasganchalenge1.rag.model

enum class EmbeddingProviderType {
    LOCAL,
    OPENAI;

    companion object {
        fun fromRaw(raw: String?): EmbeddingProviderType {
            return runCatching { valueOf(raw.orEmpty()) }.getOrDefault(LOCAL)
        }
    }
}
