package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
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
    data class Error(val message: String) : TranscriptionState
}

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

    private val _isOpenRouterPostProcessingEnabled = MutableStateFlow(sharedPrefs.getBoolean("openrouter_post_processing_enabled", true))
    val isOpenRouterPostProcessingEnabled: StateFlow<Boolean> = _isOpenRouterPostProcessingEnabled.asStateFlow()

    private val _openRouterPostProcessingModel = MutableStateFlow(sharedPrefs.getString("openrouter_post_processing_model", "nvidia/nemotron-3-ultra-550b-a55b:free") ?: "nvidia/nemotron-3-ultra-550b-a55b:free")
    val openRouterPostProcessingModel: StateFlow<String> = _openRouterPostProcessingModel.asStateFlow()

    private val defaultSystemPrompt = """
        Você é um taquígrafo profissional de plenário de altíssima competência. Sua tarefa é transcrever o áudio fornecido seguindo rigorosamente a norma-padrão da Língua Portuguesa (incluindo pontuação, concordância e ortografia oficial). 

        IMPORTANTE:
        1. Formate a transcrição com quebras de linha lógicas e parágrafos estruturados para garantir excelente legibilidade.
        2. Se houver mais de um orador ou seções claras de debate, indique as mudanças de fala de forma elegante (exemplo: 'Orador 1:', 'Orador 2:' ou '[Intervenção]').
        3. Preserve toda a formalidade e termos jurídicos/parlamentares típicos de sessões parlamentares.
        4. Não adicione comentários, resumos ou notas pessoais. Apenas transcreva o áudio de forma fidedigna e formate-o de maneira impecável.
    """.trimIndent()

    private val _systemPrompt = MutableStateFlow(sharedPrefs.getString("system_prompt", defaultSystemPrompt) ?: defaultSystemPrompt)
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    fun hasEffectiveKey(providerId: String): Boolean = apiKeyResolver.hasEffectiveKey(providerId)

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

        _selectedFile.value = FileInfo(name, size, mimeType)
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
        openRouterPostProcessingModel: String
    ) {
        _selectedProvider.value = provider
        _apiKey.value = key
        _openRouterApiKey.value = openRouterKey
        _selectedModel.value = model
        _systemPrompt.value = prompt
        _isOpenRouterPostProcessingEnabled.value = openRouterPostProcessingEnabled
        _openRouterPostProcessingModel.value = openRouterPostProcessingModel

        sharedPrefs.edit().apply {
            putString("selected_provider", provider)
            putString("custom_api_key", key)
            putString("openrouter_api_key", openRouterKey)
            putString("selected_model", model)
            putString("system_prompt", prompt)
            putBoolean("openrouter_post_processing_enabled", openRouterPostProcessingEnabled)
            putString("openrouter_post_processing_model", openRouterPostProcessingModel)
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
                val base64Data = withContext(Dispatchers.IO) {
                    readUriAsBase64(uri)
                }

                if (base64Data == null) {
                    _transcriptionState.value = TranscriptionState.Error("Não foi possível ler o arquivo de áudio selecionado.")
                    return@launch
                }

                _transcriptionState.value = TranscriptionState.Transcribing

                val provider = providerRegistry.get(_selectedProvider.value)
                if (provider == null) {
                    _transcriptionState.value = TranscriptionState.Error("Provedor desconhecido: ${_selectedProvider.value}")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    provider.transcribe(
                        TranscriptionRequest(
                            audioBase64 = base64Data,
                            fileInfo = fileInfo,
                            model = _selectedModel.value,
                            systemPrompt = _systemPrompt.value
                        )
                    )
                }

                val transcriptionResult = result.getOrElse { e ->
                    _transcriptionState.value = TranscriptionState.Error(e.message ?: "Erro desconhecido na transcrição.")
                    return@launch
                }

                val transcriptText = transcriptionResult.text
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

                val savedAudioUri = withContext(Dispatchers.IO) {
                    saveUriToInternalStorage(uri, fileInfo.name)
                } ?: uri

                val newEntity = TranscriptionEntity(
                    title = fileInfo.name.substringBeforeLast("."),
                    fileName = fileInfo.name,
                    fileSize = fileInfo.size,
                    mimeType = fileInfo.mimeType,
                    transcriptText = transcriptText,
                    modelUsed = _selectedModel.value,
                    audioUri = savedAudioUri.toString(),
                    segmentsJson = segmentsJson,
                    audioDurationMs = transcriptionResult.durationMs
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
