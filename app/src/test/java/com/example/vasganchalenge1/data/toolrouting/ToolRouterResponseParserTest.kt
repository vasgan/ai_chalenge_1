package com.example.vasganchalenge1.data.toolrouting

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

    @Test
    fun `parse no_tool`() {
        val result = parser.parse("{\"action\":\"no_tool\"}", tools)
        assertTrue(result is ToolResolution.NoTool)
    }

    @Test
    fun `parse tool_call from fenced json`() {
        val raw = """
            ```json
            {"action":"tool_call","tool":"github_get_user","arguments":{"username":"octocat"}}
            ```
        """.trimIndent()

        val result = parser.parse(raw, tools)
        assertTrue(result is ToolResolution.ToolCall)
        result as ToolResolution.ToolCall
        assertEquals("github_get_user", result.toolName)
        assertEquals("{\"username\":\"octocat\"}", result.argumentsJson)
    }

    @Test
    fun `invalid json falls back to no_tool`() {
        val result = parser.parse("not-json", tools)
        assertTrue(result is ToolResolution.NoTool)
    }

    @Test
    fun `unknown tool falls back to no_tool`() {
        val result = parser.parse(
            "{\"action\":\"tool_call\",\"tool\":\"github_unknown\",\"arguments\":{}}",
            tools
        )
        assertTrue(result is ToolResolution.NoTool)
    }

    @Test
    fun `missing required argument returns clarification`() {
        val result = parser.parse(
            "{\"action\":\"tool_call\",\"tool\":\"github_get_repo\",\"arguments\":{\"owner\":\"octocat\"}}",
            tools
        )
        assertTrue(result is ToolResolution.ClarificationNeeded)
    }
}
