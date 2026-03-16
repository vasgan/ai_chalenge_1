package com.example.vasganchalenge1.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalEmbeddingProvider

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenAiEmbeddingProviderQualifier
