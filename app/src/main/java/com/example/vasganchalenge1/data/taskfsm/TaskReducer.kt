package com.example.vasganchalenge1.data.taskfsm

object TaskReducer {
    fun reduce(state: TaskState, event: TaskEvent): TaskState {
        return when (event) {
            is TaskEvent.ResetRequested -> initialTaskState(state.taskId, event.occurredAt)
            is TaskEvent.PauseRequested -> state.copy(
                status = TaskStatus.PAUSED,
                updatedAt = event.occurredAt
            )
            is TaskEvent.ResumeRequested -> state.copy(
                status = if (state.status == TaskStatus.PAUSED) TaskStatus.ACTIVE else state.status,
                updatedAt = event.occurredAt
            )
            is TaskEvent.CancelRequested -> state.copy(
                status = TaskStatus.CANCELLED,
                expectedAction = ExpectedAction.Idle("Task cancelled"),
                updatedAt = event.occurredAt
            )
            is TaskEvent.UserMessage -> reduceUserMessage(state, event)
            is TaskEvent.ToolResult -> reduceToolResult(state, event)
        }
    }

    private fun reduceUserMessage(
        state: TaskState,
        event: TaskEvent.UserMessage
    ): TaskState {
        if (state.status == TaskStatus.PAUSED ||
            state.status == TaskStatus.CANCELLED ||
            state.status == TaskStatus.ERROR
        ) {
            return state
        }

        return when (val step = state.currentStep) {
            is TaskStep.CollectRequirements -> {
                val updatedFields = step.collectedFields + extractRequirements(
                    text = event.text,
                    known = step.collectedFields
                )
                val missing = missingRequiredFields(updatedFields)
                if (missing.isNotEmpty()) {
                    state.copy(
                        currentStep = step.copy(
                            missingFields = missing,
                            collectedFields = updatedFields
                        ),
                        expectedAction = ExpectedAction.UserReply(
                            prompt = "Не хватает данных по задаче: ${missing.joinToString()}",
                            missingFields = missing
                        ),
                        updatedAt = event.occurredAt
                    )
                } else {
                    state.copy(
                        phase = TaskPhase.PLANNING,
                        currentStep = TaskStep.CreatePlan(requirements = updatedFields),
                        expectedAction = ExpectedAction.ToolCall(
                            toolName = "LLM_PLAN",
                            hint = updatedFields.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                        ),
                        updatedAt = event.occurredAt
                    )
                }
            }

            else -> state.copy(updatedAt = event.occurredAt)
        }
    }

    private fun reduceToolResult(
        state: TaskState,
        event: TaskEvent.ToolResult
    ): TaskState {
        if (state.status == TaskStatus.CANCELLED) return state

        return when (event.toolName) {
            "LLM_PLAN" -> {
                val planSummary = event.output.take(280)
                state.copy(
                    phase = TaskPhase.EXECUTION,
                    currentStep = TaskStep.ImplementFeature(
                        featureKey = "main",
                        planSummary = planSummary
                    ),
                    expectedAction = ExpectedAction.ToolCall(
                        toolName = "CODEGEN",
                        hint = planSummary
                    ),
                    status = if (event.success) TaskStatus.ACTIVE else TaskStatus.ERROR,
                    updatedAt = event.occurredAt
                )
            }

            "CODEGEN" -> {
                val featureKey = (state.currentStep as? TaskStep.ImplementFeature)?.featureKey ?: "main"
                state.copy(
                    phase = TaskPhase.VALIDATION,
                    currentStep = TaskStep.RunChecks(targetFeatureKey = featureKey),
                    expectedAction = ExpectedAction.ToolCall(
                        toolName = "RUN_CHECKS",
                        hint = "Validate feature=$featureKey"
                    ),
                    status = if (event.success) TaskStatus.ACTIVE else TaskStatus.ERROR,
                    updatedAt = event.occurredAt
                )
            }

            "RUN_CHECKS" -> {
                if (event.success) {
                    state.copy(
                        phase = TaskPhase.DONE,
                        currentStep = TaskStep.Finished(summary = event.output.take(280)),
                        expectedAction = ExpectedAction.Idle("Task completed"),
                        status = TaskStatus.ACTIVE,
                        updatedAt = event.occurredAt
                    )
                } else {
                    state.copy(
                        phase = TaskPhase.EXECUTION,
                        currentStep = TaskStep.ImplementFeature(
                            featureKey = "fixes",
                            planSummary = event.output.take(280)
                        ),
                        expectedAction = ExpectedAction.ToolCall(
                            toolName = "CODEGEN",
                            hint = "Apply fixes based on: ${event.output.take(240)}"
                        ),
                        status = TaskStatus.ACTIVE,
                        updatedAt = event.occurredAt
                    )
                }
            }

            else -> state.copy(updatedAt = event.occurredAt)
        }
    }

    private fun extractRequirements(text: String, known: Map<String, String>): Map<String, String> {
        val normalized = text.trim()
        if (normalized.isBlank()) return emptyMap()

        val pairs = linkedMapOf<String, String>()
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }

        lines.forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                if (key in REQUIRED_FIELDS && value.isNotBlank()) {
                    pairs[key] = value.take(280)
                }
            }
        }

        if ("goal" !in known && "goal" !in pairs) {
            pairs["goal"] = normalized.take(280)
        }
        if ("constraints" !in known && "constraints" !in pairs && lines.size > 1) {
            pairs["constraints"] = if (lines.size > 2) {
                lines.drop(1).joinToString(" ").take(280)
            } else {
                lines.last().take(280)
            }
        }
        if ("constraints" !in known && "constraints" !in pairs && normalized.length >= 60) {
            // For natural one-shot user requests, use the request body as baseline constraints.
            pairs["constraints"] = normalized.take(280)
        }

        return pairs
    }
}

private val REQUIRED_FIELDS = listOf("goal", "constraints")

private fun missingRequiredFields(fields: Map<String, String>): List<String> {
    val missing = mutableListOf<String>()
    val goal = fields["goal"].orEmpty().trim()
    val constraints = fields["constraints"].orEmpty().trim()

    if (goal.length < 20) {
        missing += "goal (минимум 20 символов и конкретный ожидаемый результат)"
    }
    if (constraints.length < 10) {
        missing += "constraints (ограничения/условия/рамки)"
    }
    return missing
}
