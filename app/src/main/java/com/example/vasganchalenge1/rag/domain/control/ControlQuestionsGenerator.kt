package com.example.vasganchalenge1.rag.domain.control

import com.example.vasganchalenge1.data.network.ApiService
import com.example.vasganchalenge1.data.network.ResponsesInputContent
import com.example.vasganchalenge1.data.network.ResponsesInputItem
import com.example.vasganchalenge1.data.network.ResponsesRequest
import com.example.vasganchalenge1.rag.model.GeneratedControlQuestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ControlQuestionsGenerator @Inject constructor(
    private val apiService: ApiService
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(summary: ControlQuestionsKnowledgeSummary): Result<List<GeneratedControlQuestion>> {
        return runCatching {
            val response = apiService.responses(
                ResponsesRequest(
                    model = MODEL,
                    input = listOf(
                        ResponsesInputItem(
                            role = "system",
                            content = listOf(
                                ResponsesInputContent(
                                    type = "input_text",
                                    text = SYSTEM_PROMPT
                                )
                            )
                        ),
                        ResponsesInputItem(
                            role = "user",
                            content = listOf(
                                ResponsesInputContent(
                                    type = "input_text",
                                    text = buildUserPrompt(summary)
                                )
                            )
                        )
                    )
                )
            )

            val rawText = extractOutputText(response)
            parseQuestions(rawText)
        }
    }

    internal fun parseQuestions(raw: String): List<GeneratedControlQuestion> {
        val jsonBlock = extractJsonObject(raw) ?: error("LLM router returned invalid JSON")
        val root = json.parseToJsonElement(jsonBlock).jsonObject
        val questionsArray = root["questions"]?.jsonArray ?: error("Поле questions отсутствует")
        require(questionsArray.size == 10) { "Ожидалось 10 вопросов, получено ${questionsArray.size}" }

        return questionsArray.mapIndexed { index, element ->
            val obj = element.jsonObject
            val question = obj["question"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val expectation = obj["expectation"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val expectedSources = obj["expectedSources"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()

            require(question.isNotBlank()) { "Question[$index] is blank" }
            require(expectation.isNotBlank()) { "Expectation[$index] is blank" }
            require(expectedSources.isNotEmpty()) { "expectedSources[$index] is empty" }

            GeneratedControlQuestion(
                question = question,
                expectation = expectation,
                expectedSources = expectedSources
            )
        }
    }

    private fun extractOutputText(response: com.example.vasganchalenge1.data.network.ResponsesApiResponse): String {
        val direct = response.output_text?.trim().orEmpty()
        if (direct.isNotBlank()) return direct

        val fromOutput = response.output.orEmpty()
            .flatMap { it.content.orEmpty() }
            .mapNotNull { it.text?.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        if (fromOutput.isNotBlank()) return fromOutput
        error("Пустой ответ от модели при генерации контрольных вопросов")
    }

    private fun buildUserPrompt(summary: ControlQuestionsKnowledgeSummary): String {
        return """
            Generate 10 control questions for this indexed knowledge base.

            Index ID: ${summary.indexId}

            Documents summary:
            ${summary.documentsSummary}

            Sections summary:
            ${summary.sectionsSummary}

            Key topics:
            ${summary.topicsSummary}

            Return JSON:
            {
              "questions": [
                {
                  "question": "...",
                  "expectation": "...",
                  "expectedSources": ["..."]
                }
              ]
            }
        """.trimIndent()
    }

    private fun extractJsonObject(raw: String): String? {
        val cleaned = raw
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    private companion object {
        const val MODEL = "gpt-5-mini"

        const val SYSTEM_PROMPT = """
            You generate control questions for a local RAG knowledge base.

            Return STRICT JSON only.
            Do not add explanations.
            Do not use markdown.
            Do not wrap JSON in code fences.

            Generate exactly 10 control questions.

            Language rule:
            - question and expectation MUST be in Russian.
            - Keep expectedSources as file/section references from the provided knowledge base.

            Each item must contain:
            - question
            - expectation
            - expectedSources

            Rules:
            - Questions must be answerable ONLY using the provided knowledge base summary
            - Cover different parts of the system
            - Avoid trivial questions
            - expectedSources must refer to files/sections
            - expectation must be short and concrete
        """
    }
}
