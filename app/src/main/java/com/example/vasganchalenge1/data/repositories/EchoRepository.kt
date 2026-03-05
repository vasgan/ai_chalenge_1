package com.example.vasganchalenge1.data.repositories

import com.example.vasganchalenge1.data.ChatRequest
import com.example.vasganchalenge1.data.LongTermMemory
import com.example.vasganchalenge1.data.LongTermMemoryPatch
import com.example.vasganchalenge1.data.LongTermMemoryWritePlan
import com.example.vasganchalenge1.data.Message
import com.example.vasganchalenge1.data.Role
import com.example.vasganchalenge1.data.UiChatMessage
import com.example.vasganchalenge1.data.WorkingMemoryPatch
import com.example.vasganchalenge1.data.WorkingMemoryState
import com.example.vasganchalenge1.data.WorkingMemoryStatus
import com.example.vasganchalenge1.data.WorkingMemoryWritePlan
import com.example.vasganchalenge1.data.network.ApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject

class EchoRepository  @Inject constructor(
    private val api: ApiService,
    moshi: Moshi
) {
    private val workingMemoryWritePlanAdapter = moshi.adapter(WorkingMemoryWritePlan::class.java)
    private val longTermWritePlanAdapter = moshi.adapter(LongTermMemoryWritePlan::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )

    suspend fun send(
        settings: AppSettings,
        history: List<UiChatMessage>,
        facts: String,
        longTermMemoryJson: String,
        invariants: List<String>,
        workingContext: String
    ): DataResponse {
        val messages = mutableListOf<Message>()
        val systemParts = buildList {
            if (longTermMemoryJson.isNotBlank() && longTermMemoryJson != "{}") {
                add(longTermMemoryJson)
            }
            if (invariants.isNotEmpty()) {
                val normalized = invariants.mapIndexed { index, invariant ->
                    "${index + 1}. ${invariant.trim()}"
                }.joinToString("\n")
                add(
                    "[PROFILE_INVARIANTS]\n$normalized\n[/PROFILE_INVARIANTS]\n" +
                            "You must explicitly enforce these invariants.\n" +
                            "Refuse to provide any solution that violates any invariant.\n" +
                            "If user request conflicts with invariants, explain refusal and offer compliant alternatives."
                )
            }
            if (workingContext.isNotBlank()) {
                add(workingContext)
            }
            if (settings.contextMode == ContextMode.FACTS && facts.isNotBlank()) {
                add("Conversation facts from older messages:\n$facts")
            }
            if (settings.enabled) {
                add("${settings.format}. ${settings.lengthLimit}.")
            }
        }
        if (systemParts.isNotEmpty()) {
            messages += Message(
                role = "system",
                content = systemParts.joinToString("\n\n")
            )
        }
        val historyMessages = history.map {
            Message(
                role = if (it.role == Role.USER) "user" else "assistant",
                content = it.text
            )
        }
        messages.addAll(historyMessages)
        val response = api.chatCompletion(
            ChatRequest(
                model = settings.model,
                messages = messages,
                stop = if (settings.enabled) settings.stopSequence else null,
                max_tokens = if (settings.enabled) settings.maxTokens else null,
                temperature = if (settings.enabled) settings.temperature.toDouble() else null
            )
        )
        return DataResponse(
            content = response.choices.firstOrNull()?.message?.content,
            tokensIn = response.usage?.prompt_tokens,
            tokenOut = response.usage?.completion_tokens
        )
    }

    suspend fun extractFacts(
        currentFacts: String,
        chunk: List<UiChatMessage>,
        settings: AppSettings
    ): String {
        if (chunk.isEmpty()) return currentFacts

        val factsMessages = buildList {
            add(
                Message(
                    "system",
                    "You extract and update durable facts from a conversation.\n" +
                            "Rules:\n" +
                            "- Keep only durable facts, decisions, preferences, and constraints.\n" +
                            //"- Keep open TODOs/questions.\n" +
                            "- Be concise (max 800 chars).\n" +
                            "- Avoid transient chatter.\n" +
                            "- Return ONLY the updated facts text."
                )
            )
            add(Message("user", "Current facts:\n${currentFacts.ifBlank { "(empty)" }}"))
            add(
                Message(
                    "user",
                    "New messages to incorporate:\n" +
                            chunk.joinToString("\n") { "${it.role}: ${it.text}" }
                )
            )
        }

        val response = api.chatCompletion(
            ChatRequest(
                model = settings.model,
                messages = factsMessages,
                stop = null,
                max_tokens = null,
                temperature = null
            )
        )

        return response.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifBlank { currentFacts }
    }

    suspend fun extractWorkingMemoryWritePlan(
        settings: AppSettings,
        currentState: WorkingMemoryState,
        userMessage: UiChatMessage,
        assistantMessage: UiChatMessage
    ): WorkingMemoryWritePlan? {
        val response = api.chatCompletion(
            ChatRequest(
                model = settings.model,
                messages = buildList {
                    add(
                        Message(
                            "system",
                            "You update task working memory.\n" +
                                    "Return ONLY valid JSON exactly matching this Kotlin shape:\n" +
                                    "{\n" +
                                    "  \"patch\": {\n" +
                                    "    \"setGoal\": \"optional string or null\",\n" +
                                    "    \"addConstraints\": [\"string\"],\n" +
                                    "    \"removeConstraints\": [\"string\"],\n" +
                                    "    \"addDecisions\": [\"string\"],\n" +
                                    "    \"removeDecisions\": [\"string\"],\n" +
                                    "    \"addOpenQuestions\": [\"string\"],\n" +
                                    "    \"closeOpenQuestions\": [\"string\"],\n" +
                                    "    \"addNextSteps\": [\"string\"],\n" +
                                    "    \"removeNextSteps\": [\"string\"],\n" +
                                    "    \"putArtifacts\": {\"artifactId\": \"description\"},\n" +
                                    "    \"removeArtifacts\": [\"artifactId\"],\n" +
                                    "    \"setStatus\": \"NEW|IN_PROGRESS|BLOCKED|DONE\",\n" +
                                    "    \"clearAll\": false\n" +
                                    "  },\n" +
                                    "  \"reason\": \"non-empty string\",\n" +
                                    "  \"confidence\": 0.85\n" +
                                    "}\n" +
                                    "Do not use keys like 'decisions' or object arrays for decisions.\n" +
                                    "Use ONLY addDecisions as array of strings.\n" +
                                    "Only include changes supported by the latest user and assistant messages.\n" +
                                    "Do not rewrite the whole state.\n" +
                                    "Use confidence in range 0..1.\n" +
                                    "Use a non-empty reason.\n" +
                                    "If there is nothing worth saving, still return a VALID non-empty patch, for example setStatus.\n" +
                                    "Return JSON only. No markdown."
                        )
                    )
                    add(
                        Message(
                            "user",
                            "Current WorkingMemoryState JSON:\n${workingMemoryStateToJson(currentState)}"
                        )
                    )
                    add(
                        Message(
                            "user",
                            "Latest user message:\n${userMessage.text}"
                        )
                    )
                    add(
                        Message(
                            "user",
                            "Latest assistant message:\n${assistantMessage.text}"
                        )
                    )
                    add(
                        Message(
                            "user",
                            "Return JSON for WorkingMemoryWritePlan only."
                        )
                    )
                },
                stop = null,
                max_tokens = 500,
                temperature = 0.0
            )
        )

        val raw = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (raw.isBlank()) return null

        val json = extractJsonObject(raw) ?: return null
        return runCatching { workingMemoryWritePlanAdapter.fromJson(json) }.getOrNull()
            ?: fallbackParseWorkingMemoryWritePlan(json)
    }

    suspend fun detectInvariantViolation(
        settings: AppSettings,
        invariants: List<String>,
        assistantMessage: String
    ): Boolean {
        if (invariants.isEmpty() || assistantMessage.isBlank()) return false
        val response = api.chatCompletion(
            ChatRequest(
                model = settings.model,
                messages = buildList {
                    add(
                        Message(
                            "system",
                            "You are a strict invariant checker.\n" +
                                    "Return ONLY JSON: {\"violates\":true|false}.\n" +
                                    "Set violates=true if assistant message proposes or encourages violating any invariant."
                        )
                    )
                    add(
                        Message(
                            "user",
                            "Invariants:\n${invariants.joinToString("\n") { "- $it" }}"
                        )
                    )
                    add(
                        Message(
                            "user",
                            "Assistant message:\n$assistantMessage"
                        )
                    )
                },
                stop = null,
                max_tokens = 50,
                temperature = 0.0
            )
        )

        val raw = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        val json = extractJsonObject(raw) ?: return false
        val parsed = runCatching { mapAdapter.fromJson(json) }.getOrNull()
        return parsed?.get("violates") as? Boolean ?: false
    }

    suspend fun extractLongTermMemoryWritePlan(
        settings: AppSettings,
        currentState: LongTermMemory,
        userMessage: UiChatMessage,
        assistantMessage: UiChatMessage
    ): LongTermMemoryWritePlan? {
        val response = api.chatCompletion(
            ChatRequest(
                model = settings.model,
                messages = buildList {
                    add(
                        Message(
                            "system",
                            "You update profile long-term memory.\n" +
                                    "Return ONLY valid JSON exactly matching this Kotlin shape:\n" +
                                    "{\n" +
                                    "  \"patch\": {\n" +
                                    "    \"setProfileDescription\": \"optional string or null\",\n" +
                                    "    \"setCommunicationLanguage\": \"optional string or null\",\n" +
                                    "    \"putCustomFields\": {\"key\": \"value\"},\n" +
                                    "    \"removeCustomFields\": [\"key\"],\n" +
                                    "    \"clearAll\": false\n" +
                                    "  },\n" +
                                    "  \"reason\": \"non-empty string\",\n" +
                                    "  \"confidence\": 0.85\n" +
                                    "}\n" +
                                    "Capture only durable user profile information: identity, stable preferences, persistent constraints, and long-lived communication preferences.\n" +
                                    "Do not store transient task details, one-off next steps, or conversation-local facts.\n" +
                                    "If there is nothing worth saving, still return a VALID non-empty patch, for example setCommunicationLanguage.\n" +
                                    "Return JSON only. No markdown."
                        )
                    )
                    add(Message("user", "Current LongTermMemory JSON:\n${longTermMemoryToJson(currentState)}"))
                    add(Message("user", "Latest user message:\n${userMessage.text}"))
                    add(Message("user", "Latest assistant message:\n${assistantMessage.text}"))
                    add(Message("user", "Return JSON for LongTermMemoryWritePlan only."))
                },
                stop = null,
                max_tokens = 400,
                temperature = 0.0
            )
        )

        val raw = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (raw.isBlank()) return null

        val json = extractJsonObject(raw) ?: return null
        return runCatching { longTermWritePlanAdapter.fromJson(json) }.getOrNull()
            ?: fallbackParseLongTermMemoryWritePlan(json)
    }

    private fun fallbackParseWorkingMemoryWritePlan(json: String): WorkingMemoryWritePlan? {
        val root = runCatching { mapAdapter.fromJson(json) }.getOrNull() ?: return null
        val patchMap = root["patch"] as? Map<*, *> ?: return null

        val reason = (root["reason"] as? String)?.takeIf { it.isNotBlank() }
            ?: "Fallback parsed WM update from assistant response."
        val confidence = (root["confidence"] as? Number)?.toDouble() ?: 0.6

        val addDecisions = buildList {
            val direct = patchMap["addDecisions"] as? List<*>
            direct?.forEach { item ->
                (item as? String)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }

            val legacy = patchMap["decisions"] as? List<*>
            legacy?.forEach { item ->
                when (item) {
                    is String -> if (item.isNotBlank()) add(item)
                    is Map<*, *> -> {
                        val option = item["option"] as? String
                        val itemReason = item["reason"] as? String
                        val combined = listOfNotNull(option, itemReason)
                            .joinToString(" - ")
                            .trim()
                        if (combined.isNotBlank()) add(combined)
                    }
                }
            }
        }

        val patch = WorkingMemoryPatch(
            setGoal = patchMap["setGoal"] as? String,
            addConstraints = stringList(patchMap["addConstraints"]),
            removeConstraints = stringList(patchMap["removeConstraints"]),
            addDecisions = addDecisions,
            removeDecisions = stringList(patchMap["removeDecisions"]),
            addOpenQuestions = stringList(patchMap["addOpenQuestions"]),
            closeOpenQuestions = stringList(patchMap["closeOpenQuestions"]),
            addNextSteps = stringList(patchMap["addNextSteps"]),
            removeNextSteps = stringList(patchMap["removeNextSteps"]),
            putArtifacts = stringMap(patchMap["putArtifacts"]),
            removeArtifacts = stringList(patchMap["removeArtifacts"]),
            setStatus = parseStatus(patchMap["setStatus"]),
            clearAll = patchMap["clearAll"] as? Boolean ?: false
        )

        return WorkingMemoryWritePlan(
            patch = patch,
            reason = reason,
            confidence = confidence.coerceIn(0.0, 1.0)
        )
    }

    private fun fallbackParseLongTermMemoryWritePlan(json: String): LongTermMemoryWritePlan? {
        val root = runCatching { mapAdapter.fromJson(json) }.getOrNull() ?: return null
        val patchMap = root["patch"] as? Map<*, *> ?: return null

        val reason = (root["reason"] as? String)?.takeIf { it.isNotBlank() }
            ?: "Fallback parsed long-term memory update."
        val confidence = (root["confidence"] as? Number)?.toDouble() ?: 0.6

        val patch = LongTermMemoryPatch(
            setProfileDescription = patchMap["setProfileDescription"] as? String
                ?: patchMap["profileDescription"] as? String,
            setCommunicationLanguage = patchMap["setCommunicationLanguage"] as? String
                ?: patchMap["communicationLanguage"] as? String,
            putCustomFields = stringMap(
                patchMap["putCustomFields"] ?: patchMap["customFields"] ?: patchMap["fields"]
            ),
            removeCustomFields = stringList(
                patchMap["removeCustomFields"] ?: patchMap["removeFields"]
            ),
            clearAll = patchMap["clearAll"] as? Boolean ?: false
        )

        return LongTermMemoryWritePlan(
            patch = patch,
            reason = reason,
            confidence = confidence.coerceIn(0.0, 1.0)
        )
    }
}

data class DataResponse(val content: String?, val tokensIn: Int?, val tokenOut: Int?)

private fun workingMemoryStateToJson(state: WorkingMemoryState): String {
    return buildString {
        append("{")
        append("\"taskId\":\"").append(escapeJson(state.taskId)).append("\",")
        append("\"goal\":")
        if (state.goal == null) append("null") else append("\"").append(escapeJson(state.goal)).append("\"")
        append(",\"constraints\":[").append(state.constraints.joinToString(",") { "\"${escapeJson(it)}\"" }).append("]")
        append(",\"decisions\":[").append(state.decisions.joinToString(",") { "\"${escapeJson(it)}\"" }).append("]")
        append(",\"openQuestions\":[").append(state.openQuestions.joinToString(",") { "\"${escapeJson(it)}\"" }).append("]")
        append(",\"nextSteps\":[").append(state.nextSteps.joinToString(",") { "\"${escapeJson(it)}\"" }).append("]")
        append(",\"artifacts\":{")
        append(state.artifacts.entries.joinToString(",") { "\"${escapeJson(it.key)}\":\"${escapeJson(it.value)}\"" })
        append("}")
        append(",\"status\":\"").append(state.status.name).append("\"")
        append(",\"updatedAt\":").append(state.updatedAt)
        append("}")
    }
}

private fun longTermMemoryToJson(state: LongTermMemory): String {
    return buildString {
        append("{")
        append("\"mode\":\"").append(state.mode.name).append("\",")
        append("\"profileDescription\":\"").append(escapeJson(state.profileDescription)).append("\",")
        append("\"communicationLanguage\":\"").append(escapeJson(state.communicationLanguage)).append("\",")
        append("\"customFields\":{")
        append(state.customFields.joinToString(",") {
            "\"${escapeJson(it.key)}\":\"${escapeJson(it.value)}\""
        })
        append("},")
        append("\"updatedAt\":").append(state.updatedAt)
        append("}")
    }
}

private fun extractJsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return raw.substring(start, end + 1)
}

private fun stringList(value: Any?): List<String> {
    return (value as? List<*>)?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }.orEmpty()
}

private fun stringMap(value: Any?): Map<String, String> {
    return (value as? Map<*, *>)?.mapNotNull { entry ->
        val key = entry.key as? String ?: return@mapNotNull null
        val itemValue = entry.value as? String ?: return@mapNotNull null
        if (key.isBlank() || itemValue.isBlank()) null else key to itemValue
    }?.toMap().orEmpty()
}

private fun parseStatus(value: Any?): WorkingMemoryStatus? {
    val raw = value as? String ?: return null
    return runCatching { enumValueOf<WorkingMemoryStatus>(raw) }.getOrNull()
}

private fun escapeJson(value: String): String {
    return buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
