package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.ChatResponse
import com.example.vasganchalenge1.data.Choice
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.pipeline.McpPipelineDescriptor
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
        McpTool(name = "github_get_user", requiredParams = listOf("username")),
        McpTool(name = "github_get_repo", requiredParams = listOf("owner", "repo")),
        McpTool(name = "github_list_repo_issues", requiredParams = listOf("owner", "repo")),
        McpTool(name = "summarize_github_report", requiredParams = listOf("userJson", "repoJson", "issuesJson")),
        McpTool(name = "save_summary_to_file", requiredParams = listOf("title", "summaryText", "rawJson"))
    )
    private val pipelines = listOf(
        McpPipelineDescriptor(
            name = "cross_server_github_report_flow",
            description = "pipeline",
            requiredArgs = listOf("username", "repo"),
            requiredTools = listOf(
                "github_get_user",
                "github_get_repo",
                "github_list_repo_issues",
                "summarize_github_report",
                "save_summary_to_file"
            ),
            steps = emptyList()
        ),
        McpPipelineDescriptor(
            name = "github_user_tracking_flow",
            description = "tracking pipeline",
            requiredArgs = listOf("username"),
            requiredTools = listOf("github_schedule_user_stars_tracking", "github_get_user_stars_stats"),
            steps = emptyList()
        )
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
            availableTools = tools,
            availablePipelines = pipelines
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
            availableTools = tools,
            availablePipelines = pipelines
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
            availableTools = tools,
            availablePipelines = pipelines
        )

        assertTrue(result is ToolResolution.ToolCall)
        result as ToolResolution.ToolCall
        assertEquals("github_stop_user_stars_tracking", result.toolName)
        assertEquals("{}", result.argumentsJson)
    }

    @Test
    fun `fallback resolves summary pipeline command`() = runBlocking {
        val router = NaturalLanguageToolRouter(noToolApi)
        val result = router.resolve(
            settings = AppSettings(),
            userMessage = "Собери профиль пользователя octocat, репозиторий Hello-World, сделай краткую сводку и сохрани результат",
            availableTools = tools,
            availablePipelines = pipelines
        )

        assertTrue(result is ToolResolution.PipelineCall)
        result as ToolResolution.PipelineCall
        assertEquals("cross_server_github_report_flow", result.pipelineName)
        assertEquals("{\"username\":\"octocat\",\"repo\":\"Hello-World\"}", result.argumentsJson)
    }

    @Test
    fun `fallback resolves tracking pipeline command`() = runBlocking {
        val router = NaturalLanguageToolRouter(noToolApi)
        val result = router.resolve(
            settings = AppSettings(),
            userMessage = "Запусти трекинг для пользователя octocat и потом покажи статистику за 12 часов",
            availableTools = tools,
            availablePipelines = pipelines
        )

        assertTrue(result is ToolResolution.PipelineCall)
        result as ToolResolution.PipelineCall
        assertEquals("github_user_tracking_flow", result.pipelineName)
        assertTrue(result.argumentsJson.contains(""""username":"octocat""""))
        assertTrue(result.argumentsJson.contains(""""period":"12h""""))
    }
}
