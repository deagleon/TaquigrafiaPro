package com.example.data.provider

import com.example.data.api.Segment
import com.example.ui.FileInfo

data class TranscriptionRequest(
    val audioBase64: String,
    val fileInfo: FileInfo,
    val model: String,
    val systemPrompt: String,
)

data class TranscriptionResult(
    val text: String,
    val segments: List<Segment>? = null,
    val durationMs: Int? = null,
    val language: String? = null,
)

interface TranscriptionProvider {
    val id: String
    val displayName: String
    fun isConfigured(): Boolean
    suspend fun transcribe(request: TranscriptionRequest): Result<TranscriptionResult>
    fun availableModels(): List<ModelOption>
}
