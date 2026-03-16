package com.example.mcpserver

interface McpToolRegistry {
    fun listTools(): List<Map<String, Any?>>
    suspend fun callTool(name: String, arguments: Map<String, Any?>): Map<String, Any?>
}

