package com.example.vasganchalenge1.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object McpModule {

    @Provides
    @Singleton
    fun provideMcpHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(SSE)
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 10_000
            }
        }
    }
}
