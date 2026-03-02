package com.example.vasganchalenge1.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.TaskItem
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskListUiState(
    val profileId: String = "",
    val profileTitle: String = "",
    val tasks: List<TaskItem> = emptyList()
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val store: ChatStoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val _state = MutableStateFlow(TaskListUiState(profileId = profileId))
    val state = _state

    init {
        viewModelScope.launch {
            store.profilesFlow.collect { profiles ->
                val profile = profiles.firstOrNull { it.id == profileId } ?: return@collect
                _state.value = TaskListUiState(
                    profileId = profile.id,
                    profileTitle = profile.title,
                    tasks = profile.tasks
                )
            }
        }
    }

    fun createTask(title: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val task = store.createTask(profileId, title)
            onDone(task.id)
        }
    }
}
