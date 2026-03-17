package com.example.vasganchalenge1.rag.model

import java.util.UUID

data class ControlQuestion(
    val id: String = UUID.randomUUID().toString(),
    val indexId: String,
    val question: String,
    val expectation: String,
    val expectedSources: List<String>
)

enum class ControlQuestionsMode {
    EDITABLE,
    READ_ONLY;

    companion object {
        fun fromRoute(raw: String?): ControlQuestionsMode {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: EDITABLE
        }
    }
}

data class GeneratedControlQuestion(
    val question: String,
    val expectation: String,
    val expectedSources: List<String>
)
