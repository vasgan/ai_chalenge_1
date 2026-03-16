package com.example.vasganchalenge1.di

import android.content.Context
import com.example.vasganchalenge1.rag.data.local.RagDao
import com.example.vasganchalenge1.rag.data.local.RagDatabase
import com.example.vasganchalenge1.rag.domain.embedding.AiEdgeTextEmbeddingProvider
import com.example.vasganchalenge1.rag.domain.embedding.EmbeddingProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RagBindingModule {

    @Binds
    @Singleton
    abstract fun bindEmbeddingProvider(
        impl: AiEdgeTextEmbeddingProvider
    ): EmbeddingProvider
}

@Module
@InstallIn(SingletonComponent::class)
object RagModule {

    @Provides
    @Singleton
    fun provideRagDatabase(@ApplicationContext context: Context): RagDatabase {
        return RagDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideRagDao(database: RagDatabase): RagDao {
        return database.ragDao()
    }
}
