package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.TranscriptionEntity
import com.example.data.TranscriptionRepository
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.InlineData
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.api.OpenRouterTranscriptionRequest
import com.example.data.api.OpenRouterChatCompletionRequest
import com.example.data.api.ChatMessage
import com.example.data.api.InputAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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

    // Reactive list of past transcriptions
    val transcriptionsHistory: StateFlow<List<TranscriptionEntity>> = repository.allTranscriptions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current selected audio file info
    private val _selectedFile = MutableStateFlow<FileInfo?>(null)
    val selectedFile: StateFlow<FileInfo?> = _selectedFile.asStateFlow()

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    // Current state of transcription action
    private val _transcriptionState = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState.asStateFlow()

    // Preferences configuration
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
        // Normalise common mime-types or fallback
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
                // Read and encode audio file bytes in background
                val base64Data = withContext(Dispatchers.IO) {
                    readUriAsBase64(uri)
                }

                if (base64Data == null) {
                    _transcriptionState.value = TranscriptionState.Error("Não foi possível ler o arquivo de áudio selecionado.")
                    return@launch
                }

                _transcriptionState.value = TranscriptionState.Transcribing

                var transcriptText: String?
                val provider = _selectedProvider.value

                if (provider == "openrouter") {
                    val activeOpenRouterKey = _openRouterApiKey.value.trim()
                    if (activeOpenRouterKey.isEmpty()) {
                        _transcriptionState.value = TranscriptionState.Error(
                            "Chave de API do OpenRouter não configurada. Por favor, insira sua chave nas Configurações (ícone de engrenagem no topo)."
                        )
                        return@launch
                    }

                    // Deduce format
                    val extension = fileInfo.name.substringAfterLast(".").lowercase()
                    val format = when (extension) {
                        "wav" -> "wav"
                        "mp3" -> "mp3"
                        "m4a" -> "m4a"
                        "ogg" -> "ogg"
                        "flac" -> "flac"
                        "aac" -> "aac"
                        else -> "mp3"
                    }

                    val request = OpenRouterTranscriptionRequest(
                        model = _selectedModel.value,
                        inputAudio = InputAudio(
                            data = base64Data,
                            format = format
                        )
                    )

                    val authHeader = "Bearer $activeOpenRouterKey"
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.openRouterService.transcribeAudio(
                            authorization = authHeader,
                            request = request
                        )
                    }

                    if (response.error != null) {
                        _transcriptionState.value = TranscriptionState.Error(
                            "Erro do OpenRouter: ${response.error.message ?: "Erro sem mensagem"}"
                        )
                        return@launch
                    }

                    val rawTranscript = response.text
                    if (rawTranscript.isNullOrBlank()) {
                        _transcriptionState.value = TranscriptionState.Error("O OpenRouter não retornou nenhum texto para esta transcrição.")
                        return@launch
                    }

                    if (_isOpenRouterPostProcessingEnabled.value) {
                        val chatRequest = OpenRouterChatCompletionRequest(
                            model = _openRouterPostProcessingModel.value,
                            messages = listOf(
                                ChatMessage(role = "system", content = _systemPrompt.value),
                                ChatMessage(role = "user", content = "Abaixo está a transcrição bruta do áudio. Por favor, reescreva-a seguindo rigorosamente as instruções do sistema, corrigindo pontuação, gramática, ortografia, quebras de linha e estruturação:\n\n$rawTranscript")
                            )
                        )

                        val chatResponse = withContext(Dispatchers.IO) {
                            RetrofitClient.openRouterService.chatCompletion(
                                authorization = authHeader,
                                request = chatRequest
                            )
                        }

                        if (chatResponse.error != null) {
                            _transcriptionState.value = TranscriptionState.Error(
                                "Erro no pós-processamento do OpenRouter: ${chatResponse.error.message ?: "Erro sem mensagem"}"
                            )
                            return@launch
                        }

                        transcriptText = chatResponse.choices?.firstOrNull()?.message?.content
                    } else {
                        transcriptText = rawTranscript
                    }
                } else {
                    // Resolve API key
                    val activeKey = _apiKey.value.trim().ifEmpty {
                        BuildConfig.GEMINI_API_KEY
                    }

                    if (activeKey.isEmpty() || activeKey == "MY_GEMINI_API_KEY") {
                        _transcriptionState.value = TranscriptionState.Error(
                            "Chave de API do Gemini não configurada. Por favor, insira sua chave nas Configurações (ícone de engrenagem no topo)."
                        )
                        return@launch
                    }

                    // Construct Request
                    val request = GenerateContentRequest(
                        contents = listOf(
                            Content(
                                parts = listOf(
                                    Part(inlineData = InlineData(mimeType = fileInfo.mimeType, data = base64Data)),
                                    Part(text = "Transcreva o áudio acima seguindo rigorosamente as instruções do sistema.")
                                )
                            )
                        ),
                        systemInstruction = Content(
                            parts = listOf(Part(text = _systemPrompt.value))
                        ),
                        generationConfig = GenerationConfig(
                            temperature = 0.2f // Lower temperature for high-fidelity transcribing/reasoning
                        )
                    )

                    // Execute Call
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.service.generateContent(
                            model = _selectedModel.value,
                            apiKey = activeKey,
                            request = request
                        )
                    }

                    transcriptText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }

                if (transcriptText.isNullOrBlank()) {
                    _transcriptionState.value = TranscriptionState.Error("O provedor não retornou nenhum texto para esta transcrição.")
                    return@launch
                }

                // Save to Database
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
                    audioUri = savedAudioUri.toString()
                )

                val id = withContext(Dispatchers.IO) {
                    repository.insert(newEntity)
                }

                _transcriptionState.value = TranscriptionState.Success(transcriptText, id.toInt())

            } catch (e: Exception) {
                e.printStackTrace()
                _transcriptionState.value = TranscriptionState.Error("Erro na transcrição: ${e.localizedMessage ?: "Erro desconhecido"}")
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
