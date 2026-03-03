package com.example.vasganchalenge1.data.repositories

import com.example.vasganchalenge1.data.LongTermMemory
import com.example.vasganchalenge1.data.LongTermMemoryPatch
import com.example.vasganchalenge1.data.LongTermMemoryWritePlan
import com.example.vasganchalenge1.data.LongTermMode
import com.example.vasganchalenge1.data.MemoryField
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LongTermMemoryManager @Inject constructor(
    private val store: ChatStoreRepository
) {
    suspend fun getState(profileId: String): LongTermMemory {
        return store.getProfile(profileId)?.longTermMemory ?: LongTermMemory()
    }

    fun validateWritePlan(plan: LongTermMemoryWritePlan): ValidationResult {
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

    suspend fun updateByPlan(profileId: String, plan: LongTermMemoryWritePlan): ValidationResult {
        val validation = validateWritePlan(plan)
        if (validation is ValidationResult.Invalid) return validation

        val current = getState(profileId)
        if (current.mode != LongTermMode.AUTO) {
            return ValidationResult.Invalid("automatic updates are allowed only for AUTO mode")
        }

        val updated = applyPatch(current, plan.patch)
        store.updateProfileLongTerm(profileId, updated)
        return ValidationResult.Valid
    }

    fun applyPatch(current: LongTermMemory, patch: LongTermMemoryPatch): LongTermMemory {
        if (patch.clearAll) {
            return LongTermMemory(
                mode = current.mode,
                updatedAt = System.currentTimeMillis()
            )
        }

        val fields = LinkedHashMap<String, String>()
        current.customFields.forEach { field ->
            val key = field.key.trimTo280()
            val value = field.value.trimTo280()
            if (key.isNotBlank() && value.isNotBlank()) {
                fields[key] = value
            }
        }

        patch.removeCustomFields
            .map { it.trimTo280() }
            .forEach(fields::remove)

        patch.putCustomFields.forEach { (key, value) ->
            val normalizedKey = key.trimTo280()
            val normalizedValue = value.trimTo280()
            if (normalizedKey.isBlank() || normalizedValue.isBlank()) return@forEach
            fields.remove(normalizedKey)
            fields[normalizedKey] = normalizedValue
        }

        while (fields.size > 30) {
            val oldestKey = fields.entries.firstOrNull()?.key ?: break
            fields.remove(oldestKey)
        }

        return current.copy(
            profileDescription = patch.setProfileDescription?.trimTo280() ?: current.profileDescription,
            communicationLanguage = patch.setCommunicationLanguage?.trimTo280()
                ?: current.communicationLanguage,
            customFields = fields.entries.map { MemoryField(it.key, it.value) },
            updatedAt = System.currentTimeMillis()
        )
    }
}

private fun LongTermMemoryPatch.isEffectivelyEmpty(): Boolean {
    return setProfileDescription == null &&
            setCommunicationLanguage == null &&
            putCustomFields.isEmpty() &&
            removeCustomFields.isEmpty()
}

private fun String.trimTo280(): String = trim().take(280)
