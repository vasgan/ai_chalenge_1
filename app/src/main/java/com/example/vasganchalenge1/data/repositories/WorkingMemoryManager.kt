package com.example.vasganchalenge1.data.repositories

import com.example.vasganchalenge1.data.WorkingMemoryPatch
import com.example.vasganchalenge1.data.WorkingMemoryState
import com.example.vasganchalenge1.data.WorkingMemoryStatus
import com.example.vasganchalenge1.data.WorkingMemoryWritePlan
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

@Singleton
class WorkingMemoryManager @Inject constructor(
    private val store: ChatStoreRepository
) {
    suspend fun getState(taskId: String): WorkingMemoryState {
        return store.getTask(taskId)?.workingMemory ?: WorkingMemoryState(taskId = taskId)
    }

    fun validateWritePlan(plan: WorkingMemoryWritePlan): ValidationResult {
        if (plan.confidence !in 0.0..1.0) {
            return ValidationResult.Invalid("confidence must be in range 0..1")
        }
        if (plan.reason.isBlank()) {
            return ValidationResult.Invalid("reason must not be empty")
        }
        if (!plan.patch.clearAll && plan.patch.isEffectivelyEmpty()) {
            return ValidationResult.Invalid("patch must not be empty")
        }
        return ValidationResult.Valid
    }

    suspend fun updateByPlan(taskId: String, plan: WorkingMemoryWritePlan): ValidationResult {
        val validation = validateWritePlan(plan)
        if (validation is ValidationResult.Invalid) return validation

        val current = getState(taskId)
        val updated = applyPatch(current, plan.patch)
        store.updateTaskWorkingMemory(taskId, updated)
        return ValidationResult.Valid
    }

    fun applyPatch(
        current: WorkingMemoryState,
        patch: WorkingMemoryPatch
    ): WorkingMemoryState {
        if (patch.clearAll) {
            return WorkingMemoryState(
                taskId = current.taskId,
                updatedAt = System.currentTimeMillis()
            )
        }

        var state = current

        if (patch.setGoal != null) {
            state = state.copy(goal = patch.setGoal.trimTo280().ifBlank { null })
        }

        state = state.copy(
            constraints = normalizeList(
                current = state.constraints,
                additions = patch.addConstraints,
                removals = patch.removeConstraints,
                limit = 15
            ),
            decisions = normalizeList(
                current = state.decisions,
                additions = patch.addDecisions,
                removals = patch.removeDecisions,
                limit = 20
            ),
            openQuestions = normalizeList(
                current = state.openQuestions,
                additions = patch.addOpenQuestions,
                removals = patch.closeOpenQuestions,
                limit = 10
            ),
            nextSteps = normalizeList(
                current = state.nextSteps,
                additions = patch.addNextSteps,
                removals = patch.removeNextSteps,
                limit = 10
            ),
            artifacts = normalizeArtifacts(
                current = state.artifacts,
                putArtifacts = patch.putArtifacts,
                removeArtifacts = patch.removeArtifacts,
                limit = 30
            ),
            status = patch.setStatus ?: state.status,
            updatedAt = System.currentTimeMillis()
        )

        return state
    }

    suspend fun buildWorkingContext(taskId: String): String {
        val state = getState(taskId)
        val sections = buildList {
            state.goal?.takeIf { it.isNotBlank() }?.let { add("goal: $it") }
            if (state.constraints.isNotEmpty()) {
                add("constraints: ${state.constraints.joinToString(" | ")}")
            }
            if (state.decisions.isNotEmpty()) {
                add("decisions: ${state.decisions.joinToString(" | ")}")
            }
            if (state.openQuestions.isNotEmpty()) {
                add("open_questions: ${state.openQuestions.joinToString(" | ")}")
            }
            if (state.nextSteps.isNotEmpty()) {
                add("next_steps: ${state.nextSteps.joinToString(" | ")}")
            }
            add("status: ${state.status}")
        }

        val header = "[WORKING_MEMORY]\n"
        val footer = "\n[/WORKING_MEMORY]"
        val maxBodyLength = 1500 - header.length - footer.length

        val body = buildString {
            var first = true
            for (section in sections) {
                val candidate = if (first) section else "\n$section"
                if (length + candidate.length > maxBodyLength) {
                    val remaining = maxBodyLength - length
                    if (remaining > 0) {
                        append(candidate.take(remaining.coerceAtLeast(0)))
                    }
                    break
                }
                append(candidate)
                first = false
            }
        }

        return "$header$body$footer"
    }

    private fun normalizeList(
        current: List<String>,
        additions: List<String>,
        removals: List<String>,
        limit: Int
    ): List<String> {
        val removalSet = removals.map { it.trimTo280() }.toSet()
        val retained = current
            .map { it.trimTo280() }
            .filter { it.isNotBlank() && it !in removalSet }
            .toMutableList()

        additions
            .map { it.trimTo280() }
            .filter { it.isNotBlank() }
            .forEach { retained.add(it) }

        return retained.takeLast(limit)
    }

    private fun normalizeArtifacts(
        current: Map<String, String>,
        putArtifacts: Map<String, String>,
        removeArtifacts: List<String>,
        limit: Int
    ): Map<String, String> {
        val ordered = LinkedHashMap<String, String>()
        current.forEach { (key, value) ->
            val normalizedKey = key.trimTo280()
            val normalizedValue = value.trimTo280()
            if (normalizedKey.isNotBlank() && normalizedValue.isNotBlank()) {
                ordered[normalizedKey] = normalizedValue
            }
        }

        removeArtifacts.map { it.trimTo280() }.forEach { ordered.remove(it) }

        putArtifacts.forEach { (key, value) ->
            val normalizedKey = key.trimTo280()
            val normalizedValue = value.trimTo280()
            if (normalizedKey.isBlank() || normalizedValue.isBlank()) return@forEach
            ordered.remove(normalizedKey)
            ordered[normalizedKey] = normalizedValue
        }

        while (ordered.size > limit) {
            val oldestKey = ordered.entries.firstOrNull()?.key ?: break
            ordered.remove(oldestKey)
        }

        return ordered
    }
}

private fun WorkingMemoryPatch.isEffectivelyEmpty(): Boolean {
    return setGoal == null &&
            addConstraints.isEmpty() &&
            removeConstraints.isEmpty() &&
            addDecisions.isEmpty() &&
            removeDecisions.isEmpty() &&
            addOpenQuestions.isEmpty() &&
            closeOpenQuestions.isEmpty() &&
            addNextSteps.isEmpty() &&
            removeNextSteps.isEmpty() &&
            putArtifacts.isEmpty() &&
            removeArtifacts.isEmpty() &&
            setStatus == null
}

private fun String.trimTo280(): String = trim().take(280)
