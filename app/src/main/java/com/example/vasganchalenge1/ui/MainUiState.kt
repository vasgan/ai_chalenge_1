package com.example.vasganchalenge1.ui

import com.example.vasganchalenge1.data.repositories.DataResponse

data class MainUiState(
    val input: String = "",
    val output: DataResponse = DataResponse(null, null, null),
    val time : Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
    val metrics: List<RunMetric> = emptyList() // NEW
)