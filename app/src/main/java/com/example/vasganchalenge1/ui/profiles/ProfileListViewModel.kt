package com.example.vasganchalenge1.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vasganchalenge1.data.Profile
import com.example.vasganchalenge1.data.repositories.ChatStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileListUiState(
    val profiles: List<Profile> = emptyList()
)

@HiltViewModel
class ProfileListViewModel @Inject constructor(
    private val store: ChatStoreRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileListUiState())
    val state = _state

    init {
        viewModelScope.launch {
            store.profilesFlow.collect { profiles ->
                _state.value = ProfileListUiState(profiles = profiles)
            }
        }
    }

    fun createProfile(title: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val profile = store.createProfile(title)
            onDone(profile.id)
        }
    }
}
