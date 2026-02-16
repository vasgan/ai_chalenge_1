package com.example.vasganchalenge1.ui

data class MainUiState(
    val input: String = "",
    val output: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)