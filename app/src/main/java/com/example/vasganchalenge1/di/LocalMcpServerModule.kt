package com.example.vasganchalenge1.di

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
    fun provideLocalMcpServerManager(): LocalMcpServerManager {
        return LocalMcpServerManager()
    }
}
