package com.example.vasganchalenge1.di

import com.example.mcpserver.SummaryStorageTools
import com.example.vasganchalenge1.data.mcp.AndroidSummaryStorageTools
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SummaryToolsModule {

    @Binds
    @Singleton
    abstract fun bindSummaryStorageTools(
        impl: AndroidSummaryStorageTools
    ): SummaryStorageTools
}

