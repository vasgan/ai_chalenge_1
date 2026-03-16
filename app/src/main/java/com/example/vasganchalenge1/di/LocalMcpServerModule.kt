package com.example.vasganchalenge1.di

import com.example.mcpserver.EmbeddedMcpServer
import com.example.mcpserver.GithubMcpToolRegistry
import com.example.mcpserver.GithubTrackingTools
import com.example.mcpserver.LocalMcpServerManager
import com.example.mcpserver.SummaryStorageTools
import com.example.mcpserver.UtilityMcpToolRegistry
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
    @GithubServer
    fun provideGithubMcpToolRegistry(
        githubTrackingTools: GithubTrackingTools
    ): GithubMcpToolRegistry {
        return GithubMcpToolRegistry(githubTrackingTools = githubTrackingTools)
    }

    @Provides
    @Singleton
    @UtilityServer
    fun provideUtilityMcpToolRegistry(
        summaryStorageTools: SummaryStorageTools
    ): UtilityMcpToolRegistry {
        return UtilityMcpToolRegistry(summaryStorageTools = summaryStorageTools)
    }

    @Provides
    @Singleton
    @GithubServer
    fun provideGithubLocalMcpServerManager(
        @GithubServer githubRegistry: GithubMcpToolRegistry
    ): LocalMcpServerManager {
        val embeddedServer = EmbeddedMcpServer(
            preferredPort = 8787,
            serverName = "local-github-mcp",
            toolRegistry = githubRegistry
        )
        return LocalMcpServerManager(embeddedServer)
    }

    @Provides
    @Singleton
    @UtilityServer
    fun provideUtilityLocalMcpServerManager(
        @UtilityServer utilityRegistry: UtilityMcpToolRegistry
    ): LocalMcpServerManager {
        val embeddedServer = EmbeddedMcpServer(
            preferredPort = 8788,
            serverName = "local-utility-mcp",
            toolRegistry = utilityRegistry
        )
        return LocalMcpServerManager(embeddedServer)
    }
}
