package com.example.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApiService {
    @POST("audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://ai.studio/build",
        @Header("X-OpenRouter-Title") title: String = "Taquigrafia Pro",
        @Body request: OpenRouterTranscriptionRequest
    ): OpenRouterTranscriptionResponse

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://ai.studio/build",
        @Header("X-OpenRouter-Title") title: String = "Taquigrafia Pro",
        @Body request: OpenRouterChatCompletionRequest
    ): OpenRouterChatCompletionResponse
}
