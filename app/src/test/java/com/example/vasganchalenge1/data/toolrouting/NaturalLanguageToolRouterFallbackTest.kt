package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.ChatResponse
import com.example.vasganchalenge1.data.Choice
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.McpTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalLanguageToolRouterFallbackTest {

    private val tools = listOf(
        McpTool(name = "github_schedule_user_stars_tracking", requiredParams = listOf("username")),
        McpTool(name = "github_get_user_stars_stats"),
        McpTool(name = "github_stop_user_stars_tracking"),
        McpTool(name = "github_get_user", requiredParams = listOf("username"))
    )

    private val noToolApi = object : ApiService {
        override suspend fun chatCompletion(request: com.example.vasganchalenge1.data.ChatRequest): ChatResponse {
            return ChatResponse(
                choices = listOf(
                    Choice(message = Message(role = "assistant", content = """{"action":"no_tool"}"""))
                ),
                usage = null
            )
        }
    }

    @Test
    fun `fallback resolves start tracking command`() = runBlocking {
        val router = NaturalLanguageToolRouter(noToolApi)
        val result = router.resolve(
            settings = AppSettings(),
            userMessage = "Запусти сбор статистики звезд у пользователя octocat каждые 30 секунд",
            availableTools = tools
        )

        assertTrue(result is ToolResolution.ToolCall)
        result as ToolResolution.ToolCall
        assertEquals("github_schedule_user_stars_tracking", result.toolName)
        assertTrue(result.argumentsJson.contains(""""username":"octocat""""))
        assertTrue(result.argumentsJson.contains(""""intervalSeconds":30"""))
    }

    @Test
    fun `fallback resolves stats command`() = runBlocking {
        val router = NaturalLanguageToolRouter(noToolApi)
        val result = router.resolve(
            settings = AppSettings(),
            userMessage = "Покажи статистику за 12 часов по времени",
            availableTools = tools
        )

        assertTrue(result is ToolResolution.ToolCall)
        result as ToolResolution.ToolCall
        assertEquals("github_get_user_stars_stats", result.toolName)
        assertTrue(result.argumentsJson.contains(""""period":"12h""""))
        assertTrue(result.argumentsJson.contains(""""includeTimestamps":true"""))
    }

    @Test
    fun `fallback resolves stop tracking command`() = runBlocking {
        val router = NaturalLanguageToolRouter(noToolApi)
        val result = router.resolve(
            settings = AppSettings(),
            userMessage = "Останови сбор статистики звезд",
            availableTools = tools
        )

        assertTrue(result is ToolResolution.ToolCall)
        result as ToolResolution.ToolCall
        assertEquals("github_stop_user_stars_tracking", result.toolName)
        assertEquals("{}", result.argumentsJson)
    }
}

