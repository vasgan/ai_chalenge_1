package com.example.vasganchalenge1.di

import com.example.mcpserver.GithubTrackingTools
import com.example.vasganchalenge1.data.tracking.AndroidGithubTrackingTools
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingToolsModule {
    @Binds
    @Singleton
    abstract fun bindGithubTrackingTools(
        impl: AndroidGithubTrackingTools
    ): GithubTrackingTools
}
