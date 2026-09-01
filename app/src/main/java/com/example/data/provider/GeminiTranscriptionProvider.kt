package com.example.data.provider

import com.example.data.api.Content
import com.example.data.api.GeminiApiService
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.InlineData
import com.example.data.api.Part

class GeminiTranscriptionProvider(
    private val apiKeyResolver: ApiKeyResolver,
    private val service: GeminiApiService,
) : TranscriptionProvider {

    override val id: String = "gemini"
    override val displayName: String = "Gemini"

    override fun isConfigured(): Boolean = !apiKeyResolver.geminiKey().isNullOrEmpty()

    override fun availableModels(): List<ModelOption> = ModelCatalog.geminiModels

    override suspend fun transcribe(request: TranscriptionRequest): Result<TranscriptionResult> {
        val activeKey = apiKeyResolver.geminiKey()
            ?: return Result.failure(IllegalStateException("Chave de API do Gemini não configurada. Por favor, insira sua chave nas Configurações (ícone de engrenagem no topo)."))

        // Estimativa: ~5 tokens/seg para pt-BR, 26min ~7800 tokens + prompt → precisa 16384
        val estimatedTokens = (request.fileInfo.size / 1024).coerceAtLeast(2048) // fallback por tamanho
        val apiRequest = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = request.fileInfo.mimeType, data = request.audioBase64)),
                        Part(text = "Transcreva o áudio acima seguindo RIGOROSAMENTE as instruções do sistema. Seja literal, não invente. Se inaudível, marque [inaudível]. NÃO resuma, NÃO truncue, transcreva do início ao fim.")
                    )
                )
            ),
            systemInstruction = Content(parts = listOf(Part(text = request.systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.1f, maxOutputTokens = 16384)
        )

        val response = try {
            service.generateContent(
                model = request.model,
                apiKey = activeKey,
                request = apiRequest
            )
        } catch (e: java.net.SocketTimeoutException) {
            return Result.failure(IllegalStateException("Tempo de conexão esgotado. Áudio grande pode levar alguns minutos — verifique sua conexão e tente novamente.", e))
        } catch (e: java.io.IOException) {
            if ((e.message ?: "").contains("timeout", ignoreCase = true)) {
                return Result.failure(IllegalStateException("Tempo de conexão esgotado. Áudio grande pode levar alguns minutos — verifique sua conexão e tente novamente.", e))
            }
            throw e
        }

        val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (rawText.isNullOrBlank()) {
            return Result.failure(IllegalStateException("O provedor não retornou nenhum texto para esta transcrição."))
        }
        // Barreira anti-alucinação textual idêntica ao OpenRouter
        val cleaned = com.example.data.SegmentUtils.cleanTranscriptText(rawText.trim())
        return Result.success(TranscriptionResult(text = cleaned))
    }
}
