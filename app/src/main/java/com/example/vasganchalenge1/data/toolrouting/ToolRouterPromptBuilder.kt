package com.example.vasganchalenge1.data.toolrouting

import com.example.vasganchalenge1.data.repositories.McpTool

class ToolRouterPromptBuilder {

    fun buildSystemPrompt(tools: List<McpTool>): String {
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
                "  {\"name\":\"${escape(tool.name)}\",\"description\":\"${escape(tool.description)}\",\"required\":$required,\"input_schema\":$schema}"
            }
        }

        return """
            You are a router for MCP tools inside an Android chat assistant.
            Decide whether the user message should trigger a tool call.

            You receive available tools:
            $toolsBlock

            Return ONLY one JSON object and nothing else.

            Allowed formats:
            1) {"action":"no_tool"}
            2) {"action":"tool_call","tool":"tool_name","arguments":{...}}
            3) {"action":"clarification","message":"..."}

            Rules:
            - Choose tool_call only if the user clearly asks for data/action matching one of the tools.
            - If data is missing for a valid tool call, return clarification.
            - Never invent tools not in the provided list.
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
