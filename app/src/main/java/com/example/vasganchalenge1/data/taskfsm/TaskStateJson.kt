package com.example.vasganchalenge1.data.taskfsm

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class TaskStateJson(moshi: Moshi) {
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            Any::class.java
        )
    )

    fun toJson(state: TaskState): String {
        return mapAdapter.toJson(
            mapOf(
                "taskId" to state.taskId,
                "phase" to state.phase.name,
                "status" to state.status.name,
                "updatedAt" to state.updatedAt,
                "currentStep" to encodeStep(state.currentStep),
                "expectedAction" to encodeExpectedAction(state.expectedAction)
            )
        )
    }

    fun fromJson(json: String): TaskState? {
        val root = runCatching { mapAdapter.fromJson(json) }.getOrNull() ?: return null
        val taskId = root["taskId"] as? String ?: return null
        val phase = enumValueOfOrNull<TaskPhase>(root["phase"] as? String) ?: return null
        val status = enumValueOfOrNull<TaskStatus>(root["status"] as? String) ?: return null
        val updatedAt = (root["updatedAt"] as? Number)?.toLong() ?: return null
        val currentStep = decodeStep(root["currentStep"] as? Map<*, *>) ?: return null
        val expectedAction = decodeExpectedAction(root["expectedAction"] as? Map<*, *>) ?: return null

        return TaskState(
            taskId = taskId,
            phase = phase,
            currentStep = currentStep,
            expectedAction = expectedAction,
            status = status,
            updatedAt = updatedAt
        )
    }

    private fun encodeStep(step: TaskStep): Map<String, Any?> {
        return when (step) {
            is TaskStep.CollectRequirements -> mapOf(
                "type" to "collect_requirements",
                "missingFields" to step.missingFields,
                "collectedFields" to step.collectedFields
            )

            is TaskStep.CreatePlan -> mapOf(
                "type" to "create_plan",
                "requirements" to step.requirements
            )

            is TaskStep.ImplementFeature -> mapOf(
                "type" to "implement_feature",
                "featureKey" to step.featureKey,
                "planSummary" to step.planSummary
            )

            is TaskStep.RunChecks -> mapOf(
                "type" to "run_checks",
                "targetFeatureKey" to step.targetFeatureKey
            )

            is TaskStep.Finished -> mapOf(
                "type" to "finished",
                "summary" to step.summary
            )
        }
    }

    private fun decodeStep(step: Map<*, *>?): TaskStep? {
        step ?: return null
        return when (step["type"] as? String) {
            "collect_requirements" -> TaskStep.CollectRequirements(
                missingFields = stringList(step["missingFields"]),
                collectedFields = stringMap(step["collectedFields"])
            )

            "create_plan" -> TaskStep.CreatePlan(
                requirements = stringMap(step["requirements"])
            )

            "implement_feature" -> TaskStep.ImplementFeature(
                featureKey = step["featureKey"] as? String ?: return null,
                planSummary = step["planSummary"] as? String ?: ""
            )

            "run_checks" -> TaskStep.RunChecks(
                targetFeatureKey = step["targetFeatureKey"] as? String ?: return null
            )

            "finished" -> TaskStep.Finished(
                summary = step["summary"] as? String ?: ""
            )

            else -> null
        }
    }

    private fun encodeExpectedAction(action: ExpectedAction): Map<String, Any?> {
        return when (action) {
            is ExpectedAction.UserReply -> mapOf(
                "type" to "user_reply",
                "prompt" to action.prompt,
                "missingFields" to action.missingFields
            )

            is ExpectedAction.ToolCall -> mapOf(
                "type" to "tool_call",
                "toolName" to action.toolName,
                "hint" to action.hint
            )

            is ExpectedAction.Idle -> mapOf(
                "type" to "idle",
                "message" to action.message
            )
        }
    }

    private fun decodeExpectedAction(action: Map<*, *>?): ExpectedAction? {
        action ?: return null
        return when (action["type"] as? String) {
            "user_reply" -> ExpectedAction.UserReply(
                prompt = action["prompt"] as? String ?: "",
                missingFields = stringList(action["missingFields"])
            )

            "tool_call" -> ExpectedAction.ToolCall(
                toolName = action["toolName"] as? String ?: return null,
                hint = action["hint"] as? String ?: ""
            )

            "idle" -> ExpectedAction.Idle(
                message = action["message"] as? String ?: ""
            )

            else -> null
        }
    }

    private fun stringList(value: Any?): List<String> {
        return (value as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    }

    private fun stringMap(value: Any?): Map<String, String> {
        return (value as? Map<*, *>)?.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val itemValue = entry.value as? String ?: return@mapNotNull null
            key to itemValue
        }?.toMap().orEmpty()
    }
}

private inline fun <reified T : Enum<T>> enumValueOfOrNull(value: String?): T? {
    value ?: return null
    return runCatching { enumValueOf<T>(value) }.getOrNull()
}
