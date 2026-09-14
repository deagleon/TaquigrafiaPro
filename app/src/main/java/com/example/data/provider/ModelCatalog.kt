package com.example.data.provider

data class ModelOption(val id: String, val label: String)

object ModelCatalog {
    val geminiModels = listOf(
        ModelOption("gemini-3.5-flash", "Gemini 3.5 Flash (Ultra-Rápido)"),
        ModelOption("gemini-3.7-flash", "Gemini 3.7 Flash (Multimodal — Recomendado para 5:26)"),
        ModelOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro (Precisão Máxima)"),
    )

    val openRouterTranscriptionModels = listOf(
        // Tradicionais STT (audio/transcriptions)
        ModelOption("openai/gpt-4o-mini-transcribe", "OpenAI GPT-4o Mini Transcribe"),
        ModelOption("openai/gpt-4o-transcribe", "OpenAI GPT-4o Transcribe"),
        ModelOption("mistralai/voxtral-mini-transcribe", "Mistral Voxtral Mini Transcribe"),
        ModelOption("mistralai/voxtral-small-24b-2507-stt", "Mistral Voxtral Small 24B STT"),
        ModelOption("mistralai/voxtral-mini-3b-2507", "Mistral Voxtral Mini 3B"),
        ModelOption("nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b", "NVIDIA Nemotron 3.5 ASR 0.6B"),
        ModelOption("nvidia/parakeet-tdt-0.6b-v3", "NVIDIA Parakeet v3"),
        ModelOption("qwen/qwen3-asr-flash-2026-02-10", "Qwen3 ASR Flash"),
        ModelOption("qwen/qwen3-asr-1.7b", "Qwen3 ASR 1.7B"),
        ModelOption("qwen/qwen3-asr-0.6b", "Qwen3 ASR 0.6B"),
        ModelOption("microsoft/mai-transcribe-1.5", "Microsoft MAI-Transcribe 1.5"),
        ModelOption("google/chirp-3", "Google Chirp 3"),
        ModelOption("openai/gpt-transcribe", "OpenAI GPT Transcribe"),
        ModelOption("openai/whisper-large-v3-turbo", "OpenAI Whisper Large v3 Turbo"),
        ModelOption("fish-audio/transcribe-1", "Fish Audio Transcribe 1"),
        ModelOption("meta/muse-voice-transcribe-1.0", "Meta Muse Voice Transcribe 1.0"),
        // Multimodal via chat/completions com input_audio — evita loop Whisper (Cláudio Lima / 5:26)
        ModelOption("openai/gpt-5.6-luna", "GPT 5.6 Luna (Multimodal)"),
    )
    val openRouterPostProcessingModels = listOf(
        ModelOption("nvidia/nemotron-3-ultra-550b-a55b:free", "NVIDIA Nemotron 3 Ultra 550B (Gratuito)"),
        ModelOption("openai/gpt-oss-120b:free", "OpenAI GPT-OSS 120B (Gratuito)"),
        ModelOption("nousresearch/hermes-3-llama-3.1-405b:free", "Nous Hermes 3 Llama 3.1 405B (Gratuito)"),
        ModelOption("deepseek/deepseek-v4-flash", "DeepSeek v4 Flash"),
        ModelOption("inception/mercury-2", "Inception Mercury 2"),
    )

    /** Modelos que recusam qualquer conteiner que nao seja RIFF/WAVE (ver ADR-0004). */
    val wavOnlyModelIds = setOf("meta/muse-voice-transcribe-1.0")

    fun requiresWav(modelId: String): Boolean =
        modelId.trim().lowercase() in wavOnlyModelIds

    /** Precisa converter para WAV antes do envio: modelo exigente + arquivo nao-WAV. */
    fun needsWavConversion(modelId: String, fileName: String): Boolean {
        if (!requiresWav(modelId)) return false
        return !com.example.data.WavTranscoder.isWavFile(fileName)
    }

    fun findLabel(modelId: String): String =
        (geminiModels + openRouterTranscriptionModels + openRouterPostProcessingModels)
            .find { it.id == modelId }?.label ?: modelId

    fun shortLabel(modelId: String): String = when (modelId) {
        "gemini-3.5-flash" -> "Flash"
        "gemini-3.7-flash" -> "3.7 Flash"
        "gemini-3.1-pro-preview" -> "Pro"
        else -> findLabel(modelId)
    }
}
