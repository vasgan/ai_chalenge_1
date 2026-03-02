package com.example.vasganchalenge1.ui.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.LongTermMemory
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

data class ProfileSettingsUiState(
    val profileId: String = "",
    val profileTitle: String = "",
    val profileDescription: String = "",
    val communicationLanguage: String = "",
    val customFields: List<EditableMemoryField> = emptyList()
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
                    profileDescription = profile.longTermMemory.profileDescription,
                    communicationLanguage = profile.longTermMemory.communicationLanguage,
                    customFields = profile.longTermMemory.customFields.map {
                        EditableMemoryField(key = it.key, value = it.value)
                    }
                )
            }
        }
    }

    fun setProfileDescription(value: String) {
        _state.value = _state.value.copy(profileDescription = value)
    }

    fun setCommunicationLanguage(value: String) {
        _state.value = _state.value.copy(communicationLanguage = value)
    }

    fun addCustomField() {
        _state.value = _state.value.copy(
            customFields = _state.value.customFields + EditableMemoryField()
        )
    }

    fun updateCustomFieldKey(id: Long, value: String) {
        _state.value = _state.value.copy(
            customFields = _state.value.customFields.map {
                if (it.id == id) it.copy(key = value) else it
            }
        )
    }

    fun updateCustomFieldValue(id: Long, value: String) {
        _state.value = _state.value.copy(
            customFields = _state.value.customFields.map {
                if (it.id == id) it.copy(value = value) else it
            }
        )
    }

    fun removeCustomField(id: Long) {
        _state.value = _state.value.copy(
            customFields = _state.value.customFields.filterNot { it.id == id }
        )
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            store.updateProfileLongTerm(
                profileId = profileId,
                longTermMemory = LongTermMemory(
                    profileDescription = _state.value.profileDescription.trim(),
                    communicationLanguage = _state.value.communicationLanguage.trim(),
                    customFields = _state.value.customFields.mapNotNull {
                        val key = it.key.trim()
                        val value = it.value.trim()
                        if (key.isEmpty() || value.isEmpty()) null else MemoryField(key, value)
                    }
                )
            )
            onDone()
        }
    }
}
