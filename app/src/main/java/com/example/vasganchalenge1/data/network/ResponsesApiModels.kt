package com.example.vasganchalenge1.data.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ResponsesRequest(
    val model: String,
    val input: List<ResponsesInputItem>
)

@JsonClass(generateAdapter = true)
data class ResponsesInputItem(
    val role: String,
    val content: List<ResponsesInputContent>
)

@JsonClass(generateAdapter = true)
data class ResponsesInputContent(
    val type: String,
    val text: String
)

@JsonClass(generateAdapter = true)
data class ResponsesApiResponse(
    val output_text: String? = null,
    val output: List<ResponsesOutputItem>? = null
)

@JsonClass(generateAdapter = true)
data class ResponsesOutputItem(
    val content: List<ResponsesOutputContent>? = null
)

@JsonClass(generateAdapter = true)
data class ResponsesOutputContent(
    val type: String? = null,
    val text: String? = null
)
