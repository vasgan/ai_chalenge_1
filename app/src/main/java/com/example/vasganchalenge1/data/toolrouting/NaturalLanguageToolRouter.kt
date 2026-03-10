package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.McpTool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaturalLanguageToolRouter @Inject constructor(
    private val apiService: ApiService
) {
    private val promptBuilder = ToolRouterPromptBuilder()
    private val parser = ToolRouterResponseParser()

    suspend fun resolve(
        settings: AppSettings,
        userMessage: String,
        availableTools: List<McpTool>
    ): ToolResolution {
        if (availableTools.isEmpty()) return ToolResolution.NoTool

        val raw = runCatching {
            val response = apiService.chatCompletion(
                ChatRequest(
                    model = settings.model,
                    messages = listOf(
                        Message("system", promptBuilder.buildSystemPrompt(availableTools)),
                        Message("user", userMessage)
                    ),
                    stop = null,
                    max_tokens = 220,
                    temperature = 0.0
                )
            )
            response.choices.firstOrNull()?.message?.content.orEmpty()
        }.onFailure { throwable ->
            println("NaturalToolRouter request failed: ${throwable.message}")
        }.getOrDefault("")

        if (raw.isNotBlank()) {
            println("NaturalToolRouter raw response: $raw")
        }

        val parsed = parser.parse(raw, availableTools)
        if (parsed !is ToolResolution.NoTool) {
            println("NaturalToolRouter parsed resolution: $parsed")
            return parsed
        }

        val fallback = fallbackRuleBased(userMessage, availableTools)
        if (fallback !is ToolResolution.NoTool) {
            println("NaturalToolRouter fallback resolution: $fallback")
        }
        return fallback
    }

    private fun fallbackRuleBased(
        userMessage: String,
        availableTools: List<McpTool>
    ): ToolResolution {
        val normalized = userMessage.lowercase()
        val userTool = availableTools.firstOrNull { it.name == "github_get_user" }

        if (userTool != null && normalized.contains("репозитор")) {
            val username = extractUsername(userMessage)
            if (username != null) {
                return ToolResolution.ToolCall(
                    toolName = userTool.name,
                    argumentsJson = "{\"username\":\"$username\"}"
                )
            }
            if (normalized.contains("пользовател")) {
                return ToolResolution.ClarificationNeeded("Уточни username GitHub.")
            }
        }

        return ToolResolution.NoTool
    }

    private fun extractUsername(text: String): String? {
        val regex = Regex(
            pattern = "(?i)(?:у\\s+пользователя|пользователь|user|username)\\s+([A-Za-z0-9-]{1,39})"
        )
        return regex.find(text)?.groupValues?.getOrNull(1)
    }
}
