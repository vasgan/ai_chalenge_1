package com.example.vasganchalenge1.data.taskfsm

import javax.inject.Inject
import javax.inject.Singleton

interface TaskToolRunner {
    suspend fun run(toolName: String, hint: String, state: TaskState): String
}

@Singleton
class DefaultTaskToolRunner @Inject constructor() : TaskToolRunner {
    override suspend fun run(toolName: String, hint: String, state: TaskState): String {
        return when (toolName) {
            "LLM_PLAN" -> "OK: plan created based on requirements. $hint"
            "CODEGEN" -> {
                val featureKey = (state.currentStep as? TaskStep.ImplementFeature)?.featureKey ?: "main"
                "OK: implemented feature=$featureKey. $hint"
            }

            "RUN_CHECKS" -> {
                val target = (state.currentStep as? TaskStep.RunChecks)?.targetFeatureKey.orEmpty()
                if (target == "fixes") {
                    "OK: checks passed for feature=$target"
                } else {
                    "FAIL: checks failed for feature=$target"
                }
            }

            else -> "FAIL: unknown tool $toolName"
        }
    }
}
