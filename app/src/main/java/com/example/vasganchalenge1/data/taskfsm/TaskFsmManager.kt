package com.example.vasganchalenge1.data.taskfsm

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskFsmManager @Inject constructor(
    private val store: TaskStateStore,
    private val toolRunner: TaskToolRunner
) {
    suspend fun getOrCreate(taskId: String): TaskState {
        val existing = store.get(taskId)
        if (existing != null) return existing
        val initial = initialTaskState(taskId)
        store.save(initial)
        return initial
    }

    suspend fun dispatch(taskId: String, event: TaskEvent): TaskState {
        val current = getOrCreate(taskId)
        val reduced = TaskReducer.reduce(current, event)
        store.save(reduced)
        return runPendingTool(reduced)
    }

    suspend fun reset(taskId: String): TaskState {
        val initial = initialTaskState(taskId)
        store.save(initial)
        return initial
    }

    suspend fun runPendingTool(taskId: String): TaskState {
        val current = getOrCreate(taskId)
        return runPendingTool(current)
    }

    private suspend fun runPendingTool(state: TaskState): TaskState {
        if (state.status != TaskStatus.ACTIVE || state.expectedAction !is ExpectedAction.ToolCall) {
            return state
        }

        val action = state.expectedAction as ExpectedAction.ToolCall
        val output = runCatching { toolRunner.run(action.toolName, action.hint, state) }
            .getOrElse { throwable -> "FAIL: ${throwable.message ?: "tool execution error"}" }
        val success = output.startsWith("OK:")
        val reduced = TaskReducer.reduce(
            state,
            TaskEvent.ToolResult(
                toolName = action.toolName,
                success = success,
                output = output
            )
        )
        store.save(reduced)
        return reduced
    }
}
