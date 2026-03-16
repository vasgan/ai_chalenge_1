package com.example.vasganchalenge1.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatToolRoutingMultiServerTest {

    @Test
    fun `parse tool command with explicit server prefix`() {
        val command = parseMcpToolCommand("/tool github:github_get_user {\"username\":\"octocat\"}")
        assertNotNull(command)
        assertEquals("github", command?.serverId)
        assertEquals("github_get_user", command?.name)
        assertEquals("{\"username\":\"octocat\"}", command?.argumentsJson)
    }

    @Test
    fun `parse tool command without server keeps serverId null`() {
        val command = parseMcpToolCommand("/tool github_get_user {\"username\":\"octocat\"}")
        assertNotNull(command)
        assertNull(command?.serverId)
        assertEquals("github_get_user", command?.name)
    }
}
