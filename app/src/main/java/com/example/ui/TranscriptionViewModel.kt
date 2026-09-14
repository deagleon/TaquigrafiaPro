package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TranscriptionEntity
import com.example.data.TranscriptionRepository
import com.example.data.api.Segment
import com.example.data.provider.ApiKeyResolver
import com.example.data.provider.ProviderRegistry
import com.example.data.provider.TranscriptionRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FileInfo(val name: String, val size: Long, val mimeType: String)

sealed interface TranscriptionState {
    object Idle : TranscriptionState
    object Loading : TranscriptionState
    object Transcribing : TranscriptionState
    data class Success(val text: String, val entityId: Int) : TranscriptionState
    data class Error(val message: String, val action: ErrorAction? = null) : TranscriptionState
}

enum class ErrorAction { RETRY_WITH_WHISPER }

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("taquigrafia_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = TranscriptionRepository(database.transcriptionDao())
    private val apiKeyResolver = ApiKeyResolver(sharedPrefs)
    private val providerRegistry = ProviderRegistry.create(sharedPrefs, apiKeyResolver)

    val transcriptionsHistory: StateFlow<List<TranscriptionEntity>> = repository.allTranscriptions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedFile = MutableStateFlow<FileInfo?>(null)
    val selectedFile: StateFlow<FileInfo?> = _selectedFile.asStateFlow()

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _transcriptionState = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState.asStateFlow()

    private val _selectedProvider = MutableStateFlow(sharedPrefs.getString("selected_provider", "gemini") ?: "gemini")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _apiKey = MutableStateFlow(sharedPrefs.getString("custom_api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(sharedPrefs.getString("openrouter_api_key", "") ?: "")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(sharedPrefs.getString("selected_model", "gemini-3.5-flash") ?: "gemini-3.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()
    private val _isOpenRouterPostProcessingEnabled = MutableStateFlow(sharedPrefs.getBoolean("openrouter_post_processing_enabled", false))
    val isOpenRouterPostProcessingEnabled: StateFlow<Boolean> = _isOpenRouterPostProcessingEnabled.asStateFlow()

    private val _openRouterPostProcessingModel = MutableStateFlow(sharedPrefs.getString("openrouter_post_processing_model", "nvidia/nemotron-3-ultra-550b-a55b:free") ?: "nvidia/nemotron-3-ultra-550b-a55b:free")
    val openRouterPostProcessingModel: StateFlow<String> = _openRouterPostProcessingModel.asStateFlow()

    private val _hallucinationAggressive = MutableStateFlow(sharedPrefs.getBoolean("hallucination_aggressive", false))
    val hallucinationAggressive: StateFlow<Boolean> = _hallucinationAggressive.asStateFlow()

    private val _hallucinationThreshold = MutableStateFlow(sharedPrefs.getFloat("hallucination_threshold", 0.5f))
    val hallucinationThreshold: StateFlow<Float> = _hallucinationThreshold.asStateFlow()

    private val defaultSystemPrompt = """
        Você é um taquígrafo profissional de plenário. Sua tarefa é transcrever o áudio em português brasileiro com norma-padrão absolutamente fiel ao que foi dito.

        REGRAS INEGOCIÁVEIS (VIOLAR = ERRO GRAVE):
        1. NUNCA invente, complete, parafraseie ou adicione conteúdo que não está no áudio.
        2. Se um trecho estiver inaudível, sobreposto ou incompreensível, escreva exatamente [inaudível] — não tente adivinhar.
        3. Transcrição LITERAL: preserve cada palavra dita; corrija APENAS pontuação, ortografia e concordância dentro do dito, sem alterar sentido.
        4. Quebras de linha lógicas e parágrafos curtos para legibilidade.
        5. Se houver troca clara de orador, indique "Orador 1:", "Orador 2:" ou "[Intervenção]" apenas quando audível.
        6. Preserve formalidade parlamentar apenas quando presente no áudio; não insira jargão não dito.
        7. Não adicione resumos, comentários, explicações ou metatexto. Saída = apenas transcrição.
    """.trimIndent()

    private val _systemPrompt = MutableStateFlow(sharedPrefs.getString("system_prompt", defaultSystemPrompt) ?: defaultSystemPrompt)
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    fun hasEffectiveKey(providerId: String): Boolean = apiKeyResolver.hasEffectiveKey(providerId)

    private fun transcriptionError(e: Throwable, fallback: String): TranscriptionState.Error {
        val action = if (e is com.example.data.provider.ContainerRefusedException) {
            ErrorAction.RETRY_WITH_WHISPER
        } else null
        return TranscriptionState.Error(e.message ?: fallback, action)
    }

    /** Troca para o Whisper (aceita M4A, devolve Segmentos) e reenvia o original. */
    fun retryWithWhisper() {
        val whisper = "openai/whisper-large-v3-turbo"
        _selectedModel.value = whisper
        if (_selectedProvider.value != "openrouter") _selectedProvider.value = "openrouter"
        sharedPrefs.edit().apply {
            putString("selected_provider", "openrouter")
            putString("selected_model", whisper)
            apply()
        }
        startTranscription()
    }

    fun selectFile(uri: Uri) {
        val context = getApplication<Application>()
        var name = "audio_desconhecido"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "audio"
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val rawMimeType = context.contentResolver.getType(uri)
        val mimeType = when {
            rawMimeType != null -> rawMimeType
            name.endsWith(".mp3") -> "audio/mp3"
            name.endsWith(".wav") -> "audio/wav"
            name.endsWith(".m4a") -> "audio/m4a"
            name.endsWith(".ogg") -> "audio/ogg"
            name.endsWith(".aac") -> "audio/aac"
            else -> "audio/mpeg"
        }

        _selectedFile.value = FileInfo(com.example.data.WavTranscoder.sanitizeFileName(name), size, mimeType)
        _selectedUri.value = uri
        _transcriptionState.value = TranscriptionState.Idle
    }

    fun clearSelectedFile() {
        _selectedFile.value = null
        _selectedUri.value = null
        _transcriptionState.value = TranscriptionState.Idle
    }

    fun saveSettings(
        provider: String,
        key: String,
        openRouterKey: String,
        model: String,
        prompt: String,
        openRouterPostProcessingEnabled: Boolean,
        openRouterPostProcessingModel: String,
        hallucinationAggressive: Boolean = _hallucinationAggressive.value,
        hallucinationThreshold: Float = _hallucinationThreshold.value
    ) {
        _selectedProvider.value = provider
        _apiKey.value = key
        _openRouterApiKey.value = openRouterKey
        _selectedModel.value = model
        _systemPrompt.value = prompt
        _isOpenRouterPostProcessingEnabled.value = openRouterPostProcessingEnabled
        _openRouterPostProcessingModel.value = openRouterPostProcessingModel
        _hallucinationAggressive.value = hallucinationAggressive
        _hallucinationThreshold.value = hallucinationThreshold

        sharedPrefs.edit().apply {
            putString("selected_provider", provider)
            putString("custom_api_key", key)
            putString("openrouter_api_key", openRouterKey)
            putString("selected_model", model)
            putString("system_prompt", prompt)
            putBoolean("openrouter_post_processing_enabled", openRouterPostProcessingEnabled)
            putString("openrouter_post_processing_model", openRouterPostProcessingModel)
            putBoolean("hallucination_aggressive", hallucinationAggressive)
            putFloat("hallucination_threshold", hallucinationThreshold)
            apply()
        }
    }

    fun restoreDefaultPrompt() {
        _systemPrompt.value = defaultSystemPrompt
        sharedPrefs.edit().putString("system_prompt", defaultSystemPrompt).apply()
    }

    fun startTranscription() {
        val uri = _selectedUri.value ?: return
        val fileInfo = _selectedFile.value ?: return

        _transcriptionState.value = TranscriptionState.Loading

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                // ADR-0004: modelo exigente + nao-WAV converte uma vez; o estado segue intacto para retry.
                var workUri = uri
                var workInfo = fileInfo
                if (com.example.data.provider.ModelCatalog.needsWavConversion(_selectedModel.value, fileInfo.name)) {
                    val converted = withContext(Dispatchers.IO) {
                        com.example.data.WavTranscoder.transcodeToWav16kMono(context, uri)
                    }
                    if (converted == null) {
                        _transcriptionState.value = TranscriptionState.Error(
                            "Não foi possível converter o áudio para WAV, exigido pelo modelo " +
                                "${com.example.data.provider.ModelCatalog.findLabel(_selectedModel.value)}. " +
                                "Toque em Tentar com Whisper.",
                            ErrorAction.RETRY_WITH_WHISPER
                        )
                        return@launch
                    }
                    val wavName = fileInfo.name.substringBeforeLast(".").ifBlank { "audio" } + ".wav"
                    workUri = Uri.fromFile(converted)
                    workInfo = FileInfo(wavName, converted.length(), "audio/wav")
                }
                val retrieverDurationMs = withContext(Dispatchers.IO) { extractAudioDurationMs(workUri) }
                // Fix 5:26 hallucination: sessões plenárias de 2-8min têm silêncios entre votos e ruído de microfone
                // que causa loop autorregressivo se processado em chunk único de 5min. Força VAD 2min para isolar ruído.
                val vadSilenceRatioForChunk = if (retrieverDurationMs != null && retrieverDurationMs in 120_000..480_000) 0.4 else null
                val vadHasGapForChunk = vadSilenceRatioForChunk != null
                val needsChunking = com.example.data.AudioChunker.isChunkingNeeded(retrieverDurationMs, workInfo.size, vadSilenceRatioForChunk, vadHasGapForChunk)
                val provider = providerRegistry.get(_selectedProvider.value)
                if (provider == null) {
                    _transcriptionState.value = TranscriptionState.Error("Provedor desconhecido: ${_selectedProvider.value}")
                    return@launch
                }

                _transcriptionState.value = TranscriptionState.Transcribing

                // Função auxiliar para transcrever um único base64 (reutilizável para chunk)
                suspend fun transcribeSingle(base64: String, fInfo: FileInfo): Result<com.example.data.provider.TranscriptionResult> {
                    return withContext(Dispatchers.IO) {
                        provider.transcribe(
                            TranscriptionRequest(
                                audioBase64 = base64,
                                fileInfo = fInfo,
                                model = _selectedModel.value,
                                systemPrompt = _systemPrompt.value
                            )
                        )
                    }
                }

                val transcriptionResult: com.example.data.provider.TranscriptionResult
                var mergedDurationMs: Int? = null
                if (needsChunking) {
                    android.util.Log.d("TranscriptionVM", "long audio detected dur=${retrieverDurationMs} size=${workInfo.size} -> chunking vadRatio=$vadSilenceRatioForChunk")
                    val chunks = withContext(Dispatchers.IO) {
                        com.example.data.AudioChunker.splitIfNeeded(context, workUri, workInfo.name, retrieverDurationMs, workInfo.size, vadSilenceRatioForChunk, vadHasGapForChunk, null)
                    }
                    android.util.Log.d("DiagTrunc", "chunks=${chunks.size} starts=${chunks.map{it.startMs}}")
                    if (chunks.isEmpty()) {
                        // Fallback single
                        val base64Data = withContext(Dispatchers.IO) { readUriAsBase64(workUri) }
                            ?: run { _transcriptionState.value = TranscriptionState.Error("Não foi possível ler o arquivo de áudio selecionado."); return@launch }
                        android.util.Log.d("DiagTrunc", "file=${workInfo.name} mime=${workInfo.mimeType} size=${workInfo.size} durMs=${retrieverDurationMs} needsChunk=${needsChunking} base64Len=${base64Data.length} provider=${_selectedProvider.value} model=${_selectedModel.value}")
                        val res = transcribeSingle(base64Data, workInfo)
                        transcriptionResult = res.getOrElse { e ->
                            _transcriptionState.value = transcriptionError(e, "Erro desconhecido na transcrição.")
                            return@launch
                        }
                        mergedDurationMs = transcriptionResult.durationMs ?: retrieverDurationMs
                    } else {
                        val allTexts = mutableListOf<String>()
                        val allSegments = mutableListOf<com.example.data.api.Segment>()
                        var totalDurationMs = 0
                        var failed: Throwable? = null
                        for ((idx, chunk) in chunks.withIndex()) {
                            val chunkUri = Uri.fromFile(chunk.file)
                            val chunkBase64 = withContext(Dispatchers.IO) { readUriAsBase64(chunkUri) }
                            if (chunkBase64 == null) { failed = IllegalStateException("Falha ao ler chunk ${idx + 1}/${chunks.size}"); break }
                            android.util.Log.d("DiagTrunc", "file=${workInfo.name} mime=${workInfo.mimeType} size=${workInfo.size} durMs=${retrieverDurationMs} needsChunk=${needsChunking} base64Len=${chunkBase64.length} provider=${_selectedProvider.value} model=${_selectedModel.value} chunk=${idx + 1}/${chunks.size} startMs=${chunk.startMs}")
                            val chunkFileInfo = FileInfo(chunk.file.name, chunk.file.length(), workInfo.mimeType)
                            android.util.Log.d("TranscriptionVM", "transcribing chunk ${idx + 1}/${chunks.size} start=${chunk.startMs} dur=${chunk.durationMs}")
                            val res = transcribeSingle(chunkBase64, chunkFileInfo)
                            val chunkResult = res.getOrElse { e ->
                                failed = e
                                null
                            } ?: break
                            if (chunkResult.text.isNotBlank()) allTexts.add(chunkResult.text.trim())
                            chunkResult.segments?.let { segs ->
                                val offsetSec = chunk.startMs / 1000.0
                                segs.forEach { seg ->
                                    allSegments.add(seg.copy(start = seg.start + offsetSec, end = seg.end + offsetSec))
                                }
                            }
                            totalDurationMs += chunk.durationMs.toInt()
                        }
                        com.example.data.AudioChunker.cleanupChunks(chunks)
                        val failCopy = failed
                        if (failCopy != null) {
                            _transcriptionState.value = transcriptionError(failCopy, "Erro na transcrição chunk ${failCopy.localizedMessage}")
                            return@launch
                        }
                        if (allTexts.isEmpty()) {
                            _transcriptionState.value = TranscriptionState.Error("O provedor não retornou nenhum texto para esta transcrição (chunks).")
                            return@launch
                        }
                        val mergedText = allTexts.joinToString("\n\n")
                        // Deduplica global após merge com controle de alucinação vindo de prefs
                        val aggr = _hallucinationAggressive.value
                        val thr = _hallucinationThreshold.value
                        val cleanedSegments = com.example.data.SegmentUtils.cleanAndDeduplicate(allSegments.ifEmpty { null }, aggr, thr)
                        val mergedCleanText = if (!cleanedSegments.isNullOrEmpty()) {
                            cleanedSegments.joinToString("\n\n") { it.text.trim() }.ifBlank { mergedText }
                        } else mergedText
                        val finalText = com.example.data.SegmentUtils.cleanTranscriptText(mergedCleanText, aggr)
                        android.util.Log.d("DiagTrunc", "mergedTextLen=${finalText.length} segments=${cleanedSegments?.size} words=${finalText.split(Regex("\\s+")).size}")
                        transcriptionResult = com.example.data.provider.TranscriptionResult(
                            text = finalText,
                            segments = cleanedSegments,
                            durationMs = totalDurationMs.takeIf { it > 500 } ?: retrieverDurationMs,
                            language = "pt"
                        )
                        mergedDurationMs = transcriptionResult.durationMs
                    }
                } else {
                    val base64Data = withContext(Dispatchers.IO) { readUriAsBase64(workUri) }
                    if (base64Data == null) {
                        _transcriptionState.value = TranscriptionState.Error("Não foi possível ler o arquivo de áudio selecionado.")
                        return@launch
                    }
                    android.util.Log.d("DiagTrunc", "file=${workInfo.name} mime=${workInfo.mimeType} size=${workInfo.size} durMs=${retrieverDurationMs} needsChunk=${needsChunking} base64Len=${base64Data.length} provider=${_selectedProvider.value} model=${_selectedModel.value}")
                    val res = transcribeSingle(base64Data, workInfo)
                    transcriptionResult = res.getOrElse { e ->
                        _transcriptionState.value = transcriptionError(e, "Erro desconhecido na transcrição.")
                        return@launch
                    }
                    mergedDurationMs = transcriptionResult.durationMs
                }

                val transcriptText = transcriptionResult.text
                android.util.Log.d("Halluc", "rawTextLen=${transcriptText.length} segmentsRaw=${transcriptionResult.segments?.size} cleaned=${transcriptionResult.segments?.size} dedupedRawLen=${transcriptText.length} finalLen=${transcriptText.length} first3=${transcriptionResult.segments?.take(3)?.map{com.example.data.SegmentUtils.normalizeForComparison(it.text)}} last3=${transcriptionResult.segments?.takeLast(3)?.map{com.example.data.SegmentUtils.normalizeForComparison(it.text)}}")
                if (transcriptText.isBlank()) {
                    _transcriptionState.value = TranscriptionState.Error("O provedor não retornou nenhum texto para esta transcrição.")
                    return@launch
                }

                val segmentsJson = transcriptionResult.segments?.let { segs ->
                    try {
                        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                        val type = Types.newParameterizedType(List::class.java, Segment::class.java)
                        @Suppress("UNCHECKED_CAST")
                        val adapter = moshi.adapter<List<Segment>>(type)
                        adapter.toJson(segs)
                    } catch (_: Exception) { null }
                }

                val resolvedDurationMs = mergedDurationMs?.takeIf { it > 500 }
                    ?: retrieverDurationMs

                val savedAudioUri = withContext(Dispatchers.IO) {
                    saveUriToInternalStorage(workUri, workInfo.name)
                } ?: uri

                // Forma de onda: decode on-device uma vez; falha aqui só zera os picos.
                val peaksJson = withContext(Dispatchers.IO) {
                    try {
                        val pcm = com.example.data.WaveformUtils.decodeToMonoPcm(context, savedAudioUri, resolvedDurationMs)
                            ?: return@withContext null
                        val peaks = com.example.data.WaveformUtils.computePeaks(pcm, com.example.data.WaveformUtils.WAVEFORM_BUCKETS)
                        com.example.data.WaveformUtils.peaksToJson(peaks)
                    } catch (_: Exception) { null }
                }

                val newEntity = TranscriptionEntity(
                    title = workInfo.name.substringBeforeLast("."),
                    fileName = workInfo.name,
                    fileSize = workInfo.size,
                    mimeType = workInfo.mimeType,
                    transcriptText = transcriptText,
                    modelUsed = _selectedModel.value,
                    audioUri = savedAudioUri.toString(),
                    segmentsJson = segmentsJson,
                    audioDurationMs = resolvedDurationMs,
                    peaksJson = peaksJson
                )

                val id = withContext(Dispatchers.IO) {
                    repository.insert(newEntity)
                }

                _transcriptionState.value = TranscriptionState.Success(transcriptText, id.toInt())

            } catch (e: Exception) {
                e.printStackTrace()
                val msg = e.localizedMessage ?: ""
                val isTimeout = e is java.net.SocketTimeoutException ||
                    msg.contains("timeout", ignoreCase = true) ||
                    (e.cause?.message?.contains("timeout", ignoreCase = true) == true)
                _transcriptionState.value = if (isTimeout) {
                    TranscriptionState.Error("Tempo de conexão esgotado. Áudio grande pode levar alguns minutos — verifique sua conexão e tente novamente.")
                } else {
                    TranscriptionState.Error("Erro na transcrição: ${e.localizedMessage ?: "Erro desconhecido"}")
                }
            }
        }
    }

    private fun extractAudioDurationMs(uri: Uri): Int? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                val ctx = getApplication<Application>()
                retriever.setDataSource(ctx, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()?.coerceAtLeast(500)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) { null }
    }

    private fun saveUriToInternalStorage(uri: Uri, fileName: String): Uri? {
        val context = getApplication<Application>()
        return try {
            val localFile = java.io.File(context.filesDir, "audio_${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                localFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Uri.fromFile(localFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun renameTranscription(id: Int, newTitle: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateTitle(id, newTitle)
            }
        }
    }

    fun updateTranscriptText(id: Int, newText: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateTranscriptText(id, newText)
            }
        }
    }

    fun deleteTranscription(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = transcriptionsHistory.value.find { it.id == id }
            if (entity != null && !entity.audioUri.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(entity.audioUri)
                    if (uri.scheme == "file") {
                        val file = uri.path?.let { java.io.File(it) }
                        if (file != null && file.exists()) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            repository.deleteById(id)
        }
    }

    private fun readUriAsBase64(uri: Uri): String? {
        val context = getApplication<Application>()
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
