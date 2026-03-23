package com.example.vasganchalenge1.data.repositories

enum class ModelType {
    CLOUD,
    LOCAL;

    companion object {
        fun fromRaw(raw: String?): ModelType? {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
    }
}

