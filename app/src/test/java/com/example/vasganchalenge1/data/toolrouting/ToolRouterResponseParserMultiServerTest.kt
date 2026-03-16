package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.pipeline.McpPipelineDescriptor
import com.example.vasganchalenge1.data.repositories.McpTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRouterResponseParserMultiServerTest {

    private val parser = ToolRouterResponseParser()
    private val pipelines = emptyList<McpPipelineDescriptor>()

    @Test
    fun `tool_call with serverId routes to matching server tool`() {
        val tools = listOf(
            McpTool(name = "dup_tool", requiredParams = listOf("x"), serverId = "github", serverLabel = "GitHub"),
            McpTool(name = "dup_tool", requiredParams = listOf("x"), serverId = "utility", serverLabel = "Utility")
        )

        val result = parser.parse(
            raw = "{\"action\":\"tool_call\",\"tool\":\"dup_tool\",\"serverId\":\"utility\",\"arguments\":{\"x\":\"1\"}}",
            availableTools = tools,
            availablePipelines = pipelines
        )

        assertTrue(result is ToolResolution.ToolCall)
        result as ToolResolution.ToolCall
        assertEquals("dup_tool", result.toolName)
        assertEquals("utility", result.serverId)
        assertEquals("{\"x\":\"1\"}", result.argumentsJson)
    }

    @Test
    fun `tool_call without serverId and duplicated name asks clarification`() {
        val tools = listOf(
            McpTool(name = "dup_tool", requiredParams = listOf("x"), serverId = "github", serverLabel = "GitHub"),
            McpTool(name = "dup_tool", requiredParams = listOf("x"), serverId = "utility", serverLabel = "Utility")
        )

        val result = parser.parse(
            raw = "{\"action\":\"tool_call\",\"tool\":\"dup_tool\",\"arguments\":{\"x\":\"1\"}}",
            availableTools = tools,
            availablePipelines = pipelines
        )

        assertTrue(result is ToolResolution.ClarificationNeeded)
    }
}
