package com.example.vasganchalenge1.di

import com.example.mcpserver.EmbeddedMcpServer
import com.example.mcpserver.GithubMcpToolRegistry
import com.example.mcpserver.GithubTrackingTools
import com.example.mcpserver.LocalMcpServerManager
import com.example.mcpserver.SummaryStorageTools
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalMcpServerModule {

    @Provides
    @Singleton
    fun provideLocalMcpServerManager(
        githubTrackingTools: GithubTrackingTools,
        summaryStorageTools: SummaryStorageTools
    ): LocalMcpServerManager {
        val registry = GithubMcpToolRegistry(
            githubTrackingTools = githubTrackingTools,
            summaryStorageTools = summaryStorageTools
        )
        val embeddedServer = EmbeddedMcpServer(toolRegistry = registry)
        return LocalMcpServerManager(embeddedServer)
    }
}
