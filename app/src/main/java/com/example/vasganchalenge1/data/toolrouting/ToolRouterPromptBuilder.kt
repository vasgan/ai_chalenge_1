package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.pipeline.McpPipelineDescriptor
import com.example.vasganchalenge1.data.repositories.McpTool

class ToolRouterPromptBuilder {

    fun buildSystemPrompt(
        tools: List<McpTool>,
        pipelines: List<McpPipelineDescriptor>
    ): String {
        val toolsBlock = if (tools.isEmpty()) {
            "[]"
        } else {
            tools.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { tool ->
                val required = if (tool.requiredParams.isEmpty()) "[]" else tool.requiredParams.joinToString(
                    prefix = "[\"",
                    postfix = "\"]",
                    separator = "\",\""
                )
                val schema = tool.inputSchemaJson.ifBlank { "{}" }
                "  {\"server_id\":\"${escape(tool.serverId)}\",\"server_label\":\"${escape(tool.serverLabel)}\",\"name\":\"${escape(tool.name)}\",\"description\":\"${escape(tool.description)}\",\"required\":$required,\"input_schema\":$schema}"
            }
        }
        val pipelinesBlock = if (pipelines.isEmpty()) {
            "[]"
        } else {
            pipelines.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { pipeline ->
                val required = if (pipeline.requiredArgs.isEmpty()) {
                    "[]"
                } else {
                    pipeline.requiredArgs.joinToString(
                        prefix = "[\"",
                        postfix = "\"]",
                        separator = "\",\""
                    )
                }
                val steps = if (pipeline.stepsSummary.isEmpty()) {
                    "[]"
                } else {
                    pipeline.stepsSummary.joinToString(
                        prefix = "[\"",
                        postfix = "\"]",
                        separator = "\",\""
                    ) { escape(it) }
                }
                "  {\"name\":\"${escape(pipeline.name)}\",\"description\":\"${escape(pipeline.description)}\",\"required\":$required,\"steps\":$steps}"
            }
        }

        return """
            You are a router for MCP tools inside an Android chat assistant.
            Decide whether the user message should trigger a single MCP tool call or a pipeline call.

            You receive available tools:
            $toolsBlock

            You receive available pipelines:
            $pipelinesBlock

            Return ONLY one JSON object and nothing else.

            Allowed formats:
            1) {"action":"no_tool"}
            2) {"action":"tool_call","tool":"tool_name","serverId":"server_id","arguments":{...}}
            3) {"action":"pipeline_call","pipeline":"pipeline_name","arguments":{...}}
            4) {"action":"clarification","message":"..."}

            Rules:
            - Choose tool_call only if the user clearly asks for data/action matching one of the tools.
            - Choose pipeline_call if the user asks for multi-step flow that matches one of pipelines.
            - For tool_call, always return serverId from the provided tool list.
            - If data is missing for a valid tool call, return clarification.
            - If data is missing for a valid pipeline call, return clarification.
            - Never invent tools not in the provided list.
            - Never invent pipelines not in the provided list.
            - arguments must match selected tool schema.
            - If normal chat is better, return no_tool.
            - No markdown. No explanations. JSON only.
        """.trimIndent()
    }

    private fun escape(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }
}
