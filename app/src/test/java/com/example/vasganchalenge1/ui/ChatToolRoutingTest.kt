package com.example.vasganchalenge1.ui

import com.example.vasganchalenge1.data.toolrouting.ToolResolution
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatToolRoutingTest {

    @Test
    fun `direct tool command has highest priority`() {
        assertEquals(
            InitialChatRoute.DIRECT_TOOL,
            initialChatRoute("/tool github_get_user {\"username\":\"octocat\"}")
        )
    }

    @Test
    fun `non tool command goes to natural language route`() {
        assertEquals(
            InitialChatRoute.NATURAL_LANGUAGE,
            initialChatRoute("найди пользователя octocat на github")
        )
    }

    @Test
    fun `NoTool maps to normal chat flow`() {
        assertEquals(
            RoutedChatAction.NORMAL_CHAT,
            routedChatAction(ToolResolution.NoTool)
        )
    }

    @Test
    fun `ToolCall maps to existing tool execution flow`() {
        assertEquals(
            RoutedChatAction.EXECUTE_TOOL,
            routedChatAction(
                ToolResolution.ToolCall(
                    toolName = "github_get_user",
                    argumentsJson = "{\"username\":\"octocat\"}"
                )
            )
        )
    }
}
