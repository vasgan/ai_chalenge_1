package com.example.vasganchalenge1.rag

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.ChatResponse
import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.network.ResponsesApiResponse
import com.example.vasganchalenge1.data.network.ResponsesRequest
import com.example.vasganchalenge1.rag.domain.control.ControlQuestionsGenerator
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlQuestionsGeneratorParserTest {

    private val generator = ControlQuestionsGenerator(apiService = FakeApiService())

    @Test
    fun `parses strict json and fenced json`() {
        val json = buildQuestionsJson(10)
        val parsedDirect = generator.parseQuestions(json)
        val parsedFenced = generator.parseQuestions("```json\n$json\n```")

        assertEquals(10, parsedDirect.size)
        assertEquals(10, parsedFenced.size)
        assertEquals("Question 1", parsedDirect.first().question)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fails when not exactly ten questions`() {
        generator.parseQuestions(buildQuestionsJson(9))
    }

    private fun buildQuestionsJson(size: Int): String {
        val items = (1..size).joinToString(separator = ",") { i ->
            """
            {
              "question":"Question $i",
              "expectation":"Expectation $i",
              "expectedSources":["file$i.md/section"]
            }
            """.trimIndent()
        }
        return "{\"questions\":[${items}]}"
    }

    private class FakeApiService : ApiService {
        override suspend fun chatCompletion(request: ChatRequest): ChatResponse {
            error("Not used in this test")
        }

        override suspend fun responses(request: ResponsesRequest): ResponsesApiResponse {
            return ResponsesApiResponse(output_text = "")
        }
    }
}
