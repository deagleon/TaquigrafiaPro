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
        val wantsTimestamps = prefs.getBoolean("openrouter_timestamps_enabled", true)
        val supportsVerbose = request.model.lowercase().let { m ->
            "whisper" in m || "gpt-4o-mini-transcribe" in m || "gpt-4o-transcribe" in m || "gpt-transcribe" in m || "chirp" in m
        }
        val useVerbose = wantsTimestamps && supportsVerbose
        val base64Len = request.audioBase64.length
        if (base64Len > 25 * 1024 * 1024) {
            android.util.Log.w("OpenRouterSTT", "payload oversize base64Len=$base64Len >25MB, will rely on chunking")
        }

        suspend fun doTranscribe(
            responseFormat: String?,
            timestampGranularities: List<String>?,
        ): com.example.data.api.OpenRouterTranscriptionResponse {
            return service.transcribeAudio(
                authorization = authHeader,
                request = OpenRouterTranscriptionRequest(
                    model = request.model,
                    inputAudio = InputAudio(data = request.audioBase64, format = format),
                    language = "pt",
                    temperature = 0.0,
                    prompt = null,
                    responseFormat = responseFormat,
                    timestampGranularities = timestampGranularities,
                )
            )
        }

        fun httpErrorBody(e: retrofit2.HttpException): String = try {
            e.response()?.errorBody()?.string()?.take(600) ?: ""
        } catch (_: Exception) { "" }

        fun isHttp400(e: Exception): Boolean {
            val cause = generateSequence<Throwable>(e) { it.cause }.toList()
            return cause.any { c ->
                c is retrofit2.HttpException && c.code() == 400
            } || (e as? retrofit2.HttpException)?.code() == 400
                || (e.message ?: "").contains("HTTP 400")
                || (e.cause?.message ?: "").contains("HTTP 400")
        }

        val response = try {
            if (useVerbose) {
                try {
                    doTranscribe("verbose_json", listOf("segment"))
                } catch (e: Exception) {
                    val body = (e as? retrofit2.HttpException)?.let { httpErrorBody(it) } ?: ""
                    android.util.Log.e("OpenRouterSTT", "verbose_json failed model=${request.model} body=$body err=${e.message}")
                    if (isHttp400(e)) {
                        android.util.Log.w("OpenRouterSTT", "fallback to plain json model=${request.model}")
                        doTranscribe(null, null)
                    } else throw e
                }
            } else {
                if (wantsTimestamps && !supportsVerbose) {
                    android.util.Log.w("OpenRouterSTT", "model ${request.model} does not support verbose_json, timestamps disabled")
                }
                doTranscribe(null, null)
            }
        } catch (e: java.net.SocketTimeoutException) {
            return Result.failure(IllegalStateException("Tempo de conexão esgotado. Áudio grande pode levar alguns minutos — verifique sua conexão e tente novamente.", e))
        } catch (e: java.io.IOException) {
            if ((e.message ?: "").contains("timeout", ignoreCase = true)) {
                return Result.failure(IllegalStateException("Tempo de conexão esgotado. Áudio grande pode levar alguns minutos — verifique sua conexão e tente novamente.", e))
            }
            throw e
        } catch (e: retrofit2.HttpException) {
            val body = httpErrorBody(e)
            android.util.Log.e("OpenRouterSTT", "HTTP ${e.code()} body=$body model=${request.model}")
            if (e.code() == 400) {
                return Result.failure(IllegalStateException("Erro 400 do OpenRouter: ${body.ifEmpty { e.message() }} — tente outro modelo (whisper/gpt-4o-transcribe com timestamps) ou desative timestamps.", e))
            }
            throw e
        }

        if (response.error != null) {
            android.util.Log.e("OpenRouterSTT", "response.error code=${response.error.code} msg=${response.error.message} model=${request.model}")
            val isVerboseJsonNotSupported = response.error.code == 400 &&
                (response.error.message ?: "").contains("response_format", ignoreCase = true)
            if (isVerboseJsonNotSupported && useVerbose) {
                val fallback = try {
                    doTranscribe(null, null)
                } catch (e: Exception) {
                    return Result.failure(IllegalStateException("Erro do OpenRouter: ${response.error.message ?: "Erro sem mensagem"}", e))
                }
                if (fallback.error != null) {
                    return Result.failure(IllegalStateException("Erro do OpenRouter: ${fallback.error.message ?: "Erro sem mensagem"}"))
                }
                val fbText = fallback.text
                if (fbText.isNullOrBlank()) {
                    return Result.failure(IllegalStateException("O OpenRouter não retornou nenhum texto para esta transcrição."))
                }
                return Result.success(TranscriptionResult(text = fbText, segments = null, durationMs = fallback.duration?.let { (it * 1000).toInt() }, language = fallback.language))
            }
            return Result.failure(IllegalStateException("Erro do OpenRouter: ${response.error.message ?: "Erro sem mensagem"}"))
        }

        val rawTranscript = response.text
        if (rawTranscript.isNullOrBlank()) {
            return Result.failure(IllegalStateException("O OpenRouter não retornou nenhum texto para esta transcrição."))
        }

        val rawSegments = response.segments
        val durationMs = response.duration?.let { (it * 1000).toInt() }
        val cleanedSegments = com.example.data.SegmentUtils.cleanAndDeduplicate(rawSegments)
        val segments = cleanedSegments

        val cleanRawTranscript = if (!segments.isNullOrEmpty()) {
            segments.joinToString("\n\n") { it.text.trim() }
        } else {
            rawTranscript.trim()
        }

        // Barreira textual final: remove ciclos em texto puro
        // Guard: se limpeza cortar >50% de texto longo, é provável falso-positivo → preserva original
        val dedupedRaw = com.example.data.SegmentUtils.cleanTranscriptText(cleanRawTranscript)
        android.util.Log.d("DiagTrunc", "clean in=${cleanRawTranscript.length} out=${dedupedRaw.length} segments raw=${rawSegments?.size} cleaned=${segments?.size}")
        val finalRawText = when {
            dedupedRaw != cleanRawTranscript && dedupedRaw.length < cleanRawTranscript.length * 0.5 && cleanRawTranscript.length > 1000 -> {
                android.util.Log.w("OpenRouterSTT", "cleanTranscriptText over-pruned raw ${cleanRawTranscript.length} -> ${dedupedRaw.length}, reverting")
                cleanRawTranscript
            }
            dedupedRaw != cleanRawTranscript -> {
                android.util.Log.w("OpenRouterSTT", "cleanTranscriptText pruned raw ${cleanRawTranscript.length} -> ${dedupedRaw.length}")
                dedupedRaw
            }
            else -> cleanRawTranscript
        }

        // Detecção de truncamento suspeitosamente curto vs duração (5min ~4500 chars, 26min ~23000 chars)
        durationMs?.let { dur ->
            val expectedChars = (dur / 1000.0 * 7.5).toInt()
            if (dur > 4 * 60 * 1000 && finalRawText.length < expectedChars * 0.25 && finalRawText.length < 2000) {
                android.util.Log.w("OpenRouterSTT", "transcript suspiciously short dur=${dur}ms expected~${expectedChars} chars got ${finalRawText.length} — modelo pode ter truncado, sugerindo chunking")
            }
        }

        val postProcessingEnabled = prefs.getBoolean("openrouter_post_processing_enabled", false)
        val useRawWithTimestamps = wantsTimestamps && !segments.isNullOrEmpty()

        if (!postProcessingEnabled || useRawWithTimestamps) {
            android.util.Log.d("OpenRouterSTT", "segments raw=${rawSegments?.size} cleaned=${segments?.size} duration=${durationMs} textLen=${finalRawText.length}")
            return Result.success(TranscriptionResult(text = finalRawText, segments = segments, durationMs = durationMs, language = response.language))
        }

        val postProcessingModel = prefs.getString("openrouter_post_processing_model", "nvidia/nemotron-3-ultra-550b-a55b:free")
            ?: "nvidia/nemotron-3-ultra-550b-a55b:free"

        val strictUserPrompt = """
            Abaixo está a transcrição BRUTA já fiel ao áudio. Sua tarefa é APENAS corrigir pontuação, ortografia e quebras de linha, SEM alterar palavras, SEM adicionar ou remover conteúdo, SEM inventar. Se houver [inaudível], preserve exatamente. Mantenha literalidade absoluta. Não adicione introduções, resumos ou comentários. Texto bruto:

            $finalRawText
        """.trimIndent()

        val chatRequest = OpenRouterChatCompletionRequest(
            model = postProcessingModel,
            messages = listOf(
                ChatMessage(role = "system", content = request.systemPrompt),
                ChatMessage(role = "user", content = strictUserPrompt)
            ),
            temperature = 0.1
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

        val processedRaw = chatResponse.choices?.firstOrNull()?.message?.content
        if (processedRaw.isNullOrBlank()) {
            return Result.failure(IllegalStateException("O pós-processamento não retornou texto."))
        }
        val processedTrimmed = processedRaw.trim()
        if (processedTrimmed.length > finalRawText.length * 1.8 && finalRawText.length > 50) {
            android.util.Log.w("OpenRouterSTT", "post-processing rejected: length blowup raw=${finalRawText.length} processed=${processedTrimmed.length}")
            val fallbackClean = com.example.data.SegmentUtils.cleanTranscriptText(processedTrimmed)
            if (fallbackClean.length > finalRawText.length * 1.6) {
                return Result.success(TranscriptionResult(text = finalRawText, segments = segments, durationMs = durationMs, language = response.language))
            }
        }
        if (processedTrimmed.length < finalRawText.length * 0.6 && finalRawText.length > 1000) {
            android.util.Log.w("OpenRouterSTT", "post-processing rejected: truncated raw=${finalRawText.length} processed=${processedTrimmed.length} — LLM resumiu/truncou")
            return Result.success(TranscriptionResult(text = finalRawText, segments = segments, durationMs = durationMs, language = response.language))
        }
        val processed = com.example.data.SegmentUtils.cleanTranscriptText(processedTrimmed)
        val finalProcessed = if (processed.length < processedTrimmed.length * 0.5 && processedTrimmed.length > 1000) {
            android.util.Log.w("OpenRouterSTT", "post clean over-pruned, reverting")
            processedTrimmed
        } else processed

        return Result.success(TranscriptionResult(text = finalProcessed, segments = segments, durationMs = durationMs, language = response.language))
    }
}
