package com.example.vasganchalenge1.rag.domain.retrieval

import com.example.vasganchalenge1.data.repositories.AppSettings
import com.example.vasganchalenge1.data.repositories.EchoRepository
import javax.inject.Inject
import javax.inject.Singleton

interface RagQueryRewriterGateway {
    suspend fun rewrite(settings: AppSettings, userQuestion: String): Result<String>
}

@Singleton
class RagQueryRewriter @Inject constructor(
    private val echoRepository: EchoRepository
) : RagQueryRewriterGateway {

    override suspend fun rewrite(settings: AppSettings, userQuestion: String): Result<String> {
        return runCatching {
            val rewritten = echoRepository.rewriteRetrievalQuery(settings, userQuestion)
                ?.trim()
                .orEmpty()
            if (rewritten.isBlank()) {
                userQuestion.trim()
            } else {
                rewritten
            }
        }
    }
}
