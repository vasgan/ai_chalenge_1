package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.pipeline.McpPipelineDescriptor
import com.example.vasganchalenge1.data.repositories.McpTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRouterResponseParserTest {

    private val parser = ToolRouterResponseParser()
    private val tools = listOf(
        McpTool(
            name = "github_get_user",
            description = "Get user",
            requiredParams = listOf("username")
        ),
        McpTool(
            name = "github_get_repo",
            description = "Get repo",
            requiredParams = listOf("owner", "repo")
        )
    )
    private val pipelines = listOf(
        McpPipelineDescriptor(
            name = "github_user_summary_and_save",
            description = "pipeline",
            requiredArgs = listOf("username"),
            requiredTools = emptyList(),
            steps = emptyList()
        )
    )

    @Test
    fun `parse no_tool`() {
        val result = parser.parse("{\"action\":\"no_tool\"}", tools, pipelines)
        assertTrue(result is ToolResolution.NoTool)
    }

    @Test
    fun `parse tool_call from fenced json`() {
        val raw = """
            ```json
            {"action":"tool_call","tool":"github_get_user","arguments":{"username":"octocat"}}
            ```
        """.trimIndent()

        val result = parser.parse(raw, tools, pipelines)
        assertTrue(result is ToolResolution.ToolCall)
        result as ToolResolution.ToolCall
        assertEquals("github_get_user", result.toolName)
        assertEquals("{\"username\":\"octocat\"}", result.argumentsJson)
    }

    @Test
    fun `invalid json falls back to no_tool`() {
        val result = parser.parse("not-json", tools, pipelines)
        assertTrue(result is ToolResolution.NoTool)
    }

    @Test
    fun `unknown tool falls back to no_tool`() {
        val result = parser.parse(
            "{\"action\":\"tool_call\",\"tool\":\"github_unknown\",\"arguments\":{}}",
            tools,
            pipelines
        )
        assertTrue(result is ToolResolution.NoTool)
    }

    @Test
    fun `missing required argument returns clarification`() {
        val result = parser.parse(
            "{\"action\":\"tool_call\",\"tool\":\"github_get_repo\",\"arguments\":{\"owner\":\"octocat\"}}",
            tools,
            pipelines
        )
        assertTrue(result is ToolResolution.ClarificationNeeded)
    }

    @Test
    fun `parse pipeline_call`() {
        val result = parser.parse(
            "{\"action\":\"pipeline_call\",\"pipeline\":\"github_user_summary_and_save\",\"arguments\":{\"username\":\"octocat\"}}",
            tools,
            pipelines
        )
        assertTrue(result is ToolResolution.PipelineCall)
        result as ToolResolution.PipelineCall
        assertEquals("github_user_summary_and_save", result.pipelineName)
        assertEquals("{\"username\":\"octocat\"}", result.argumentsJson)
    }
}
