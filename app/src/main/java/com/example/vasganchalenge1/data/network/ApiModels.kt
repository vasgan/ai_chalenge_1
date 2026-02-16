package com.example.vasganchalenge1.data.network
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EchoRequest(
    val text: String
)

@JsonClass(generateAdapter = true)
data class EchoResponse(
    val result: String
)