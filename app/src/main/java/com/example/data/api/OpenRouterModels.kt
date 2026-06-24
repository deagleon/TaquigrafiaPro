package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenRouterTranscriptionRequest(
    @Json(name = "model") val model: String,
    @Json(name = "input_audio") val inputAudio: InputAudio
)

@JsonClass(generateAdapter = true)
data class InputAudio(
    @Json(name = "data") val data: String, // base64 encoded audio
    @Json(name = "format") val format: String // e.g., "mp3", "wav", "m4a", "ogg"
)

@JsonClass(generateAdapter = true)
data class OpenRouterTranscriptionResponse(
    @Json(name = "text") val text: String? = null,
    @Json(name = "error") val error: OpenRouterError? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterError(
    @Json(name = "message") val message: String? = null,
    @Json(name = "code") val code: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterChatCompletionRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<ChatMessage>
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OpenRouterChatCompletionResponse(
    @Json(name = "choices") val choices: List<ChatChoice>? = null,
    @Json(name = "error") val error: OpenRouterError? = null
)

@JsonClass(generateAdapter = true)
data class ChatChoice(
    @Json(name = "message") val message: ChatMessage? = null
)
