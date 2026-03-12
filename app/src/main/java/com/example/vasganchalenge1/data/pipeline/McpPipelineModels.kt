package com.example.vasganchalenge1.data.pipeline

data class PipelineExecutionResult(
    val success: Boolean,
    val pipelineName: String,
    val steps: List<PipelineStepResult>,
    val finalMessage: String
)

data class PipelineStepResult(
    val stepName: String,
    val toolName: String,
    val success: Boolean,
    val textResult: String?,
    val structuredResult: String?,
    val errorMessage: String?
)

data class McpPipelineDescriptor(
    val name: String,
    val description: String,
    val requiredArgs: List<String>,
    val requiredTools: List<String>,
    val stepsSummary: List<String>
)

object McpPipelineCatalog {
    val githubUserSummaryAndSave = McpPipelineDescriptor(
        name = "github_user_summary_and_save",
        description = "Получить профиль GitHub пользователя, сделать краткую сводку и сохранить локально.",
        requiredArgs = listOf("username"),
        requiredTools = listOf(
            "github_get_user",
            "summarize_github_user_profile",
            "save_summary_to_file"
        ),
        stepsSummary = listOf(
            "github_get_user",
            "summarize_github_user_profile",
            "save_summary_to_file"
        )
    )

    val githubUserTrackingFlow = McpPipelineDescriptor(
        name = "github_user_tracking_flow",
        description = "Запустить трекинг метрики пользователя GitHub, получить накопленную статистику и опционально остановить трекинг.",
        requiredArgs = listOf("username"),
        requiredTools = listOf(
            "github_schedule_user_stars_tracking",
            "github_get_user_stars_stats"
        ),
        stepsSummary = listOf(
            "github_schedule_user_stars_tracking",
            "github_get_user_stars_stats",
            "github_stop_user_stars_tracking (optional)"
        )
    )

    private val all = listOf(
        githubUserSummaryAndSave,
        githubUserTrackingFlow
    )

    fun availableFor(toolNames: Set<String>): List<McpPipelineDescriptor> {
        return all.filter { descriptor ->
            descriptor.requiredTools.all { toolNames.contains(it) }
        }
    }
}
