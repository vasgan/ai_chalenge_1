package com.example.vasganchalenge1.di

import com.example.mcpserver.EmbeddedMcpServer
import com.example.mcpserver.GithubMcpToolRegistry
import com.example.mcpserver.GithubTrackingTools
import com.example.mcpserver.LocalMcpServerManager
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
        githubTrackingTools: GithubTrackingTools
    ): LocalMcpServerManager {
        val registry = GithubMcpToolRegistry(githubTrackingTools = githubTrackingTools)
        val embeddedServer = EmbeddedMcpServer(toolRegistry = registry)
        return LocalMcpServerManager(embeddedServer)
    }
}
