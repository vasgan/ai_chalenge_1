package com.example.mcpserver

enum class LocalServerStatus {
    STOPPED, STARTING, RUNNING, ERROR
}

class LocalMcpServerManager(
    private val embeddedServer: EmbeddedMcpServer = EmbeddedMcpServer()
) {
    @Volatile
    private var status: LocalServerStatus = LocalServerStatus.STOPPED

    @Volatile
    private var lastError: String? = null

    fun start(): String {
        return try {
            status = LocalServerStatus.STARTING
            embeddedServer.start()
            status = LocalServerStatus.RUNNING
            lastError = null
            embeddedServer.url()
        } catch (e: Exception) {
            status = LocalServerStatus.ERROR
            lastError = e.message
            throw e
        }
    }

    fun stop() {
        embeddedServer.stop()
        status = LocalServerStatus.STOPPED
    }

    fun status(): LocalServerStatus = status

    fun lastError(): String? = lastError

    fun currentUrl(): String = embeddedServer.url()
}
