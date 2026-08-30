package com.example.data.provider

import android.content.SharedPreferences
import com.example.data.api.ChatMessage
import com.example.data.api.InputAudio
import com.example.data.api.OpenRouterApiService
import com.example.data.api.OpenRouterChatCompletionRequest
import com.example.data.api.OpenRouterTranscriptionRequest

class OpenRouterTranscriptionProvider(
    private val apiKeyResolver: ApiKeyResolver,
    private val service: OpenRouterApiService,
    private val prefs: SharedPreferences,
) : TranscriptionProvider {

    override val id: String = "openrouter"
    override val displayName: String = "OpenRouter"

    override fun isConfigured(): Boolean = !apiKeyResolver.openRouterKey().isNullOrEmpty()

    override fun availableModels(): List<ModelOption> = ModelCatalog.openRouterTranscriptionModels

    override suspend fun transcribe(request: TranscriptionRequest): Result<TranscriptionResult> {
        val activeKey = apiKeyResolver.openRouterKey()
            ?: return Result.failure(IllegalStateException("Chave de API do OpenRouter não configurada. Por favor, insira sua chave nas Configurações (ícone de engrenagem no topo)."))

        val extension = request.fileInfo.name.substringAfterLast(".").lowercase()
        val format = when (extension) {
            "wav" -> "wav"
            "mp3" -> "mp3"
            "m4a" -> "m4a"
            "ogg" -> "ogg"
            "flac" -> "flac"
            "aac" -> "aac"
            else -> "mp3"
        }

        val authHeader = "Bearer $activeKey"
        val isWhisper = "whisper" in request.model.lowercase()
        val wantsTimestamps = isWhisper && prefs.getBoolean("openrouter_timestamps_enabled", true)
        val transcriptionRequest = OpenRouterTranscriptionRequest(
            model = request.model,
            inputAudio = InputAudio(data = request.audioBase64, format = format),
            responseFormat = if (wantsTimestamps) "verbose_json" else null,
            timestampGranularities = if (wantsTimestamps) listOf("segment") else null,
        )

        val response = try {
            service.transcribeAudio(
                authorization = authHeader,
                request = transcriptionRequest
            )
        } catch (e: java.net.SocketTimeoutException) {
            return Result.failure(IllegalStateException("Tempo de conexão esgotado. Áudio grande pode levar alguns minutos — verifique sua conexão e tente novamente.", e))
        } catch (e: java.io.IOException) {
            if ((e.message ?: "").contains("timeout", ignoreCase = true)) {
                return Result.failure(IllegalStateException("Tempo de conexão esgotado. Áudio grande pode levar alguns minutos — verifique sua conexão e tente novamente.", e))
            }
            throw e
        }

        if (response.error != null) {
            return Result.failure(IllegalStateException("Erro do OpenRouter: ${response.error.message ?: "Erro sem mensagem"}"))
        }

        val rawTranscript = response.text
        if (rawTranscript.isNullOrBlank()) {
            return Result.failure(IllegalStateException("O OpenRouter não retornou nenhum texto para esta transcrição."))
        }

        val segments = response.segments
        val durationMs = response.duration?.let { (it * 1000).toInt() }
        val useRawWithTimestamps = wantsTimestamps && !segments.isNullOrEmpty()

        val postProcessingEnabled = prefs.getBoolean("openrouter_post_processing_enabled", true)
        if (!postProcessingEnabled || useRawWithTimestamps) {
            return Result.success(TranscriptionResult(text = rawTranscript, segments = segments, durationMs = durationMs, language = response.language))
        }

        val postProcessingModel = prefs.getString("openrouter_post_processing_model", "nvidia/nemotron-3-ultra-550b-a55b:free")
            ?: "nvidia/nemotron-3-ultra-550b-a55b:free"

        val chatRequest = OpenRouterChatCompletionRequest(
            model = postProcessingModel,
            messages = listOf(
                ChatMessage(role = "system", content = request.systemPrompt),
                ChatMessage(role = "user", content = "Abaixo está a transcrição bruta do áudio. Por favor, reescreva-a seguindo rigorosamente as instruções do sistema, corrigindo pontuação, gramática, ortografia, quebras de linha e estruturação:\n\n$rawTranscript")
            )
        )

        val chatResponse = try {
            service.chatCompletion(
                authorization = authHeader,
                request = chatRequest
            )
        } catch (e: java.net.SocketTimeoutException) {
            return Result.failure(IllegalStateException("Tempo de conexão esgotado durante o pós-processamento. Tente novamente ou desative o pós-processamento em Configurações.", e))
        } catch (e: java.io.IOException) {
            if ((e.message ?: "").contains("timeout", ignoreCase = true)) {
                return Result.failure(IllegalStateException("Tempo de conexão esgotado durante o pós-processamento. Tente novamente ou desative o pós-processamento em Configurações.", e))
            }
            throw e
        }

        if (chatResponse.error != null) {
            return Result.failure(IllegalStateException("Erro no pós-processamento do OpenRouter: ${chatResponse.error.message ?: "Erro sem mensagem"}"))
        }

        val processed = chatResponse.choices?.firstOrNull()?.message?.content
        if (processed.isNullOrBlank()) {
            return Result.failure(IllegalStateException("O pós-processamento não retornou texto."))
        }

        return Result.success(TranscriptionResult(text = processed))
    }
}
