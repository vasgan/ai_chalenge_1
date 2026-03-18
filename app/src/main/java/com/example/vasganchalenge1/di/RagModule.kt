package com.example.vasganchalenge1.di

import android.content.Context
import com.example.vasganchalenge1.rag.data.local.RagDao
import com.example.vasganchalenge1.rag.data.local.RagDatabase
import com.example.vasganchalenge1.rag.data.remote.OpenAiEmbeddingsApi
import com.example.vasganchalenge1.rag.domain.embedding.AiEdgeTextEmbeddingProvider
import com.example.vasganchalenge1.rag.domain.embedding.BuildConfigOpenAiApiKeyProvider
import com.example.vasganchalenge1.rag.domain.embedding.EmbeddingProvider
import com.example.vasganchalenge1.rag.domain.embedding.OpenAiApiKeyProvider
import com.example.vasganchalenge1.rag.domain.embedding.OpenAIEmbeddingProvider
import com.example.vasganchalenge1.rag.domain.retrieval.RagRetriever
import com.example.vasganchalenge1.rag.domain.retrieval.RagRetrieverGateway
import com.example.vasganchalenge1.rag.domain.retrieval.RagQueryRewriter
import com.example.vasganchalenge1.rag.domain.retrieval.RagQueryRewriterGateway
import com.example.vasganchalenge1.rag.domain.retrieval.RagRelevanceFilter
import com.example.vasganchalenge1.rag.domain.retrieval.RagRelevanceFilterGateway
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenAiEmbeddingsRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenAiEmbeddingsOkHttp

@Module
@InstallIn(SingletonComponent::class)
abstract class RagBindingModule {

    @Binds
    @Singleton
    abstract fun bindOpenAiApiKeyProvider(
        impl: BuildConfigOpenAiApiKeyProvider
    ): OpenAiApiKeyProvider

    @Binds
    @Singleton
    @LocalEmbeddingProvider
    abstract fun bindLocalEmbeddingProvider(
        impl: AiEdgeTextEmbeddingProvider
    ): EmbeddingProvider

    @Binds
    @Singleton
    @OpenAiEmbeddingProviderQualifier
    abstract fun bindOpenAiEmbeddingProvider(
        impl: OpenAIEmbeddingProvider
    ): EmbeddingProvider

    @Binds
    @Singleton
    abstract fun bindRagRetrieverGateway(
        impl: RagRetriever
    ): RagRetrieverGateway

    @Binds
    @Singleton
    abstract fun bindRagQueryRewriterGateway(
        impl: RagQueryRewriter
    ): RagQueryRewriterGateway

    @Binds
    @Singleton
    abstract fun bindRagRelevanceFilterGateway(
        impl: RagRelevanceFilter
    ): RagRelevanceFilterGateway
}

@Module
@InstallIn(SingletonComponent::class)
object RagModule {

    private const val OPENAI_BASE_URL = "https://api.openai.com/"

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

    @Provides
    @Singleton
    @OpenAiEmbeddingsOkHttp
    fun provideOpenAiEmbeddingsOkHttp(
        apiKeyProvider: OpenAiApiKeyProvider
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val key = apiKeyProvider.getApiKeyOrNull()
                    ?: throw IllegalStateException(
                        "OpenAI API key отсутствует. Укажите OPENAI_API_KEY в gradle.properties"
                    )
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    @OpenAiEmbeddingsRetrofit
    fun provideOpenAiEmbeddingsRetrofit(
        @OpenAiEmbeddingsOkHttp okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(OPENAI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAiEmbeddingsApi(
        @OpenAiEmbeddingsRetrofit retrofit: Retrofit
    ): OpenAiEmbeddingsApi {
        return retrofit.create(OpenAiEmbeddingsApi::class.java)
    }
}
