package com.example.vasganchalenge1.ui.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.LongTermMemory
import com.example.vasganchalenge1.data.LongTermMode
import com.example.vasganchalenge1.data.MemoryField
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditableMemoryField(
    val id: Long = System.nanoTime(),
    val key: String = "",
    val value: String = ""
)

data class EditableInvariant(
    val id: Long = System.nanoTime(),
    val value: String = ""
)

data class ProfileSettingsUiState(
    val profileId: String = "",
    val profileTitle: String = "",
    val longTermMode: LongTermMode = LongTermMode.MANUAL,
    val profileDescription: String = "",
    val communicationLanguage: String = "",
    val customFields: List<EditableMemoryField> = emptyList(),
    val invariants: List<EditableInvariant> = emptyList(),
    val isEditable: Boolean = true
)

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val store: ChatStoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val _state = MutableStateFlow(ProfileSettingsUiState(profileId = profileId))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.profilesFlow.collect { profiles ->
                val profile = profiles.firstOrNull { it.id == profileId } ?: return@collect
                _state.value = ProfileSettingsUiState(
                    profileId = profile.id,
                    profileTitle = profile.title,
                    longTermMode = profile.longTermMemory.mode,
                    profileDescription = profile.longTermMemory.profileDescription,
                    communicationLanguage = profile.longTermMemory.communicationLanguage,
                    customFields = profile.longTermMemory.customFields.map {
                        EditableMemoryField(key = it.key, value = it.value)
                    },
                    invariants = profile.invariants.map { EditableInvariant(value = it) },
                    isEditable = profile.longTermMemory.mode == LongTermMode.MANUAL
                )
            }
        }
    }

    fun setProfileDescription(value: String) {
        if (!_state.value.isEditable) return
        _state.value = _state.value.copy(profileDescription = value)
    }

    fun setCommunicationLanguage(value: String) {
        if (!_state.value.isEditable) return
        _state.value = _state.value.copy(communicationLanguage = value)
    }

    fun addCustomField() {
        if (!_state.value.isEditable) return
        _state.value = _state.value.copy(
            customFields = _state.value.customFields + EditableMemoryField()
        )
    }

    fun updateCustomFieldKey(id: Long, value: String) {
        if (!_state.value.isEditable) return
        _state.value = _state.value.copy(
            customFields = _state.value.customFields.map {
                if (it.id == id) it.copy(key = value) else it
            }
        )
    }

    fun updateCustomFieldValue(id: Long, value: String) {
        if (!_state.value.isEditable) return
        _state.value = _state.value.copy(
            customFields = _state.value.customFields.map {
                if (it.id == id) it.copy(value = value) else it
            }
        )
    }

    fun removeCustomField(id: Long) {
        if (!_state.value.isEditable) return
        _state.value = _state.value.copy(
            customFields = _state.value.customFields.filterNot { it.id == id }
        )
    }

    fun addInvariant() {
        _state.value = _state.value.copy(
            invariants = _state.value.invariants + EditableInvariant()
        )
    }

    fun updateInvariant(id: Long, value: String) {
        _state.value = _state.value.copy(
            invariants = _state.value.invariants.map {
                if (it.id == id) it.copy(value = value) else it
            }
        )
    }

    fun removeInvariant(id: Long) {
        _state.value = _state.value.copy(
            invariants = _state.value.invariants.filterNot { it.id == id }
        )
    }

    fun save(onDone: () -> Unit) {
        val snapshot = _state.value
        viewModelScope.launch {
            if (snapshot.isEditable) {
                store.updateProfileLongTerm(
                    profileId = profileId,
                    longTermMemory = LongTermMemory(
                        mode = snapshot.longTermMode,
                        profileDescription = snapshot.profileDescription.trim(),
                        communicationLanguage = snapshot.communicationLanguage.trim(),
                        customFields = snapshot.customFields.mapNotNull {
                            val key = it.key.trim()
                            val value = it.value.trim()
                            if (key.isEmpty() || value.isEmpty()) null else MemoryField(key, value)
                        }
                    )
                )
            }
            store.updateProfileInvariants(
                profileId = profileId,
                invariants = snapshot.invariants.map { it.value }
            )
            onDone()
        }
    }
}
