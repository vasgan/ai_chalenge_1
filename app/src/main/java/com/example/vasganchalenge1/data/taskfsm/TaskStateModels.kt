package com.example.vasganchalenge1.data.taskfsm

enum class TaskPhase {
    PLANNING, EXECUTION, VALIDATION, DONE
}

enum class TaskStatus {
    ACTIVE, PAUSED, CANCELLED, ERROR
}

sealed interface TaskStep {
    val id: String

    data class CollectRequirements(
        val missingFields: List<String>,
        val collectedFields: Map<String, String> = emptyMap()
    ) : TaskStep {
        override val id: String = "collect_requirements"
    }

    data class CreatePlan(
        val requirements: Map<String, String>
    ) : TaskStep {
        override val id: String = "create_plan"
    }

    data class ImplementFeature(
        val featureKey: String,
        val planSummary: String = ""
    ) : TaskStep {
        override val id: String = "implement_feature"
    }

    data class RunChecks(
        val targetFeatureKey: String
    ) : TaskStep {
        override val id: String = "run_checks"
    }

    data class Finished(
        val summary: String
    ) : TaskStep {
        override val id: String = "finished"
    }
}

sealed interface ExpectedAction {
    val type: String

    data class UserReply(
        val prompt: String,
        val missingFields: List<String> = emptyList()
    ) : ExpectedAction {
        override val type: String = "user_reply"
    }

    data class ToolCall(
        val toolName: String,
        val hint: String
    ) : ExpectedAction {
        override val type: String = "tool_call"
    }

    data class Idle(
        val message: String = ""
    ) : ExpectedAction {
        override val type: String = "idle"
    }
}

data class TaskState(
    val taskId: String,
    val phase: TaskPhase,
    val currentStep: TaskStep,
    val expectedAction: ExpectedAction,
    val status: TaskStatus,
    val updatedAt: Long
)

sealed interface TaskEvent {
    val occurredAt: Long

    data class UserMessage(
        val text: String,
        override val occurredAt: Long = System.currentTimeMillis()
    ) : TaskEvent

    data class PauseRequested(
        override val occurredAt: Long = System.currentTimeMillis()
    ) : TaskEvent

    data class ResumeRequested(
        override val occurredAt: Long = System.currentTimeMillis()
    ) : TaskEvent

    data class CancelRequested(
        override val occurredAt: Long = System.currentTimeMillis()
    ) : TaskEvent

    data class ResetRequested(
        override val occurredAt: Long = System.currentTimeMillis()
    ) : TaskEvent

    data class ToolResult(
        val toolName: String,
        val success: Boolean,
        val output: String,
        override val occurredAt: Long = System.currentTimeMillis()
    ) : TaskEvent
}

fun initialTaskState(
    taskId: String,
    now: Long = System.currentTimeMillis()
): TaskState {
    val missing = listOf("goal", "constraints")
    return TaskState(
        taskId = taskId,
        phase = TaskPhase.PLANNING,
        currentStep = TaskStep.CollectRequirements(missingFields = missing),
        expectedAction = ExpectedAction.UserReply(
            prompt = "Нужно собрать требования по задаче: ${missing.joinToString()}",
            missingFields = missing
        ),
        status = TaskStatus.ACTIVE,
        updatedAt = now
    )
}
