package com.example.vasganchalenge1.data.pipeline

import com.example.vasganchalenge1.data.repositories.MCP_SERVER_ID_GITHUB
import com.example.vasganchalenge1.data.repositories.MCP_SERVER_ID_UTILITY

data class PipelineExecutionResult(
    val success: Boolean,
    val pipelineName: String,
    val steps: List<PipelineStepResult>,
    val finalMessage: String
)

data class PipelineStepResult(
    val stepName: String,
    val serverId: String,
    val toolName: String,
    val success: Boolean,
    val textResult: String?,
    val structuredResult: String?,
    val errorMessage: String?
)

data class PipelineStepDefinition(
    val stepName: String,
    val serverId: String,
    val toolName: String
)

data class McpPipelineDescriptor(
    val name: String,
    val description: String,
    val requiredArgs: List<String>,
    val requiredTools: List<String>,
    val steps: List<PipelineStepDefinition>
) {
    val stepsSummary: List<String>
        get() = steps.map { "[${it.serverId}] ${it.toolName}" }
}

object McpPipelineCatalog {
    val crossServerGithubReportFlow = McpPipelineDescriptor(
        name = "cross_server_github_report_flow",
        description = "Получить пользователя, репозиторий и issues на GitHub сервере, затем сделать summary и сохранить на Utility сервере.",
        requiredArgs = listOf("username", "repo"),
        requiredTools = listOf(
            "github_get_user",
            "github_get_repo",
            "github_list_repo_issues",
            "summarize_github_report",
            "save_summary_to_file"
        ),
        steps = listOf(
            PipelineStepDefinition(
                stepName = "Fetch GitHub user",
                serverId = MCP_SERVER_ID_GITHUB,
                toolName = "github_get_user"
            ),
            PipelineStepDefinition(
                stepName = "Fetch GitHub repo",
                serverId = MCP_SERVER_ID_GITHUB,
                toolName = "github_get_repo"
            ),
            PipelineStepDefinition(
                stepName = "Fetch GitHub repo issues",
                serverId = MCP_SERVER_ID_GITHUB,
                toolName = "github_list_repo_issues"
            ),
            PipelineStepDefinition(
                stepName = "Summarize report",
                serverId = MCP_SERVER_ID_UTILITY,
                toolName = "summarize_github_report"
            ),
            PipelineStepDefinition(
                stepName = "Save summary",
                serverId = MCP_SERVER_ID_UTILITY,
                toolName = "save_summary_to_file"
            )
        )
    )

    // Legacy alias to keep backward compatibility for existing prompts and /pipeline usage.
    val githubUserSummaryAndSave = McpPipelineDescriptor(
        name = "github_user_summary_and_save",
        description = "Legacy alias for cross_server_github_report_flow.",
        requiredArgs = listOf("username", "repo"),
        requiredTools = crossServerGithubReportFlow.requiredTools,
        steps = crossServerGithubReportFlow.steps
    )

    val githubUserTrackingFlow = McpPipelineDescriptor(
        name = "github_user_tracking_flow",
        description = "Запустить трекинг метрики пользователя GitHub, получить накопленную статистику и опционально остановить трекинг.",
        requiredArgs = listOf("username"),
        requiredTools = listOf(
            "github_schedule_user_stars_tracking",
            "github_get_user_stars_stats"
        ),
        steps = listOf(
            PipelineStepDefinition(
                stepName = "Start tracking",
                serverId = MCP_SERVER_ID_GITHUB,
                toolName = "github_schedule_user_stars_tracking"
            ),
            PipelineStepDefinition(
                stepName = "Get tracking stats",
                serverId = MCP_SERVER_ID_GITHUB,
                toolName = "github_get_user_stars_stats"
            ),
            PipelineStepDefinition(
                stepName = "Stop tracking (optional)",
                serverId = MCP_SERVER_ID_GITHUB,
                toolName = "github_stop_user_stars_tracking"
            )
        )
    )

    private val all = listOf(
        crossServerGithubReportFlow,
        githubUserSummaryAndSave,
        githubUserTrackingFlow
    )

    fun availableFor(toolNames: Set<String>): List<McpPipelineDescriptor> {
        return all.filter { descriptor ->
            descriptor.requiredTools.all { toolNames.contains(it) }
        }
    }
}
