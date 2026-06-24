package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.TranscriptionEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.TranscriptionState
import com.example.ui.TranscriptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

enum class Screen {
    Dashboard,
    Detail,
    Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val viewModel: TranscriptionViewModel = viewModel()
    
    // UI state tracking
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var selectedTranscriptionId by remember { mutableStateOf<Int?>(null) }
    
    val history by viewModel.transcriptionsHistory.collectAsState()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val transcriptionState by viewModel.transcriptionState.collectAsState()
    
    // API configurations
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val isOpenRouterPostProcessingEnabled by viewModel.isOpenRouterPostProcessingEnabled.collectAsState()
    val openRouterPostProcessingModel by viewModel.openRouterPostProcessingModel.collectAsState()

    // File selection launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectFile(uri)
        }
    }

    // Direct redirection to Detail view on success
    LaunchedEffect(transcriptionState) {
        if (transcriptionState is TranscriptionState.Success) {
            selectedTranscriptionId = (transcriptionState as TranscriptionState.Success).entityId
            currentScreen = Screen.Detail
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            Screen.Dashboard -> "Taquigrafia Pro"
                            Screen.Detail -> "Visualizar Transcrição"
                            Screen.Settings -> "Configurações"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                navigationIcon = {
                    if (currentScreen != Screen.Dashboard) {
                        IconButton(onClick = { currentScreen = Screen.Dashboard }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                actions = {
                    if (currentScreen == Screen.Dashboard) {
                        IconButton(
                            onClick = { currentScreen = Screen.Settings },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurações",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                Screen.Dashboard -> {
                    DashboardView(
                        viewModel = viewModel,
                        history = history,
                        onPickFile = { filePickerLauncher.launch("audio/*") },
                        onViewDetail = { id ->
                            selectedTranscriptionId = id
                            currentScreen = Screen.Detail
                        }
                    )
                }
                Screen.Detail -> {
                    selectedTranscriptionId?.let { id ->
                        val entity = history.find { it.id == id }
                        if (entity != null) {
                            DetailView(
                                entity = entity,
                                onDelete = {
                                    viewModel.deleteTranscription(entity.id)
                                    currentScreen = Screen.Dashboard
                                    Toast.makeText(context, "Transcrição apagada", Toast.LENGTH_SHORT).show()
                                },
                                onRename = { newTitle ->
                                    viewModel.renameTranscription(entity.id, newTitle)
                                }
                            )
                        } else {
                            Text(
                                "Transcrição não encontrada.",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Screen.Settings -> {
                    SettingsView(
                        provider = selectedProvider,
                        apiKey = apiKey,
                        openRouterApiKey = openRouterApiKey,
                        selectedModel = selectedModel,
                        systemPrompt = systemPrompt,
                        openRouterPostProcessingEnabled = isOpenRouterPostProcessingEnabled,
                        openRouterPostProcessingModel = openRouterPostProcessingModel,
                        onSave = { provider, key, openRouterKey, model, prompt, postEnabled, postModel ->
                            viewModel.saveSettings(provider, key, openRouterKey, model, prompt, postEnabled, postModel)
                            currentScreen = Screen.Dashboard
                            Toast.makeText(context, "Configurações salvas!", Toast.LENGTH_SHORT).show()
                        },
                        onRestorePrompt = {
                            viewModel.restoreDefaultPrompt()
                            Toast.makeText(context, "Prompt padrão restaurado", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// FORMATTERS HELPERS
private fun formatBytes(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "Tamanho desconhecido"
    val kb = sizeInBytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.getDefault(), "%.2f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.1f KB", kb)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
    return sdf.format(Date(timestamp))
}

@Composable
fun DashboardView(
    viewModel: TranscriptionViewModel,
    history: List<TranscriptionEntity>,
    onPickFile: () -> Unit,
    onViewDetail: (Int) -> Unit
) {
    val selectedFile by viewModel.selectedFile.collectAsState()
    val transcriptionState by viewModel.transcriptionState.collectAsState()
    val currentModel by viewModel.selectedModel.collectAsState()
    val currentApiKey by viewModel.apiKey.collectAsState()
    val currentProvider by viewModel.selectedProvider.collectAsState()
    val currentOpenRouterApiKey by viewModel.openRouterApiKey.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aesthetic Top Soundwave / Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val isTranscribing = transcriptionState is TranscriptionState.Transcribing || 
                                 transcriptionState is TranscriptionState.Loading
            SoundWaveVisual(isAnimating = isTranscribing)
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = "Transcrição de Áudios de Plenário",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Gere textos fidedignos e formatados instantaneamente",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // New Transcription Selection Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nova Transcrição",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedFile == null) {
                    // Empty Picker Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onPickFile() }
                            .testTag("select_audio_card"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Selecionar arquivo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Selecionar Áudio de Plenário",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Suporta MP3, WAV, M4A, OGG, AAC, etc.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    // Selected File Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Áudio Selecionado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = CircleShape
                                )
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFile!!.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row {
                                Text(
                                    text = formatBytes(selectedFile!!.size),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedFile!!.mimeType.substringAfter("/").uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.clearSelectedFile() },
                            modifier = Modifier.testTag("clear_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remover áudio",
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // AI Key and Model configuration hint
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val providerLabel = if (currentProvider == "openrouter") "OpenRouter" else "Gemini"
                        val modelLabel = when (currentModel) {
                            "gemini-3.5-flash" -> "Gemini 3.5 Flash"
                            "gemini-3.1-pro-preview" -> "Gemini 3.1 Pro"
                            "mistralai/voxtral-mini-transcribe" -> "Voxtral Mini"
                            "microsoft/mai-transcribe-1.5" -> "MAI-Transcribe 1.5"
                            "nvidia/parakeet-tdt-0.6b-v3" -> "Parakeet v3"
                            "qwen/qwen3-asr-flash-2026-02-10" -> "Qwen3 ASR"
                            "google/chirp-3" -> "Chirp 3"
                            "openai/whisper-large-v3-turbo" -> "Whisper Large"
                            else -> currentModel
                        }
                        Text(
                            text = "Provedor: $providerLabel | Modelo: $modelLabel",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f)
                        )
                        
                        val isCustomKey = if (currentProvider == "openrouter") {
                            currentOpenRouterApiKey.trim().isNotEmpty()
                        } else {
                            currentApiKey.trim().isNotEmpty()
                        }
                        Text(
                            text = if (isCustomKey) "🔑 Chave ativa" else "🛠️ Chave padrão",
                            fontSize = 11.sp,
                            color = if (isCustomKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress states
                    AnimatedVisibility(visible = transcriptionState !is TranscriptionState.Idle) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            when (transcriptionState) {
                                is TranscriptionState.Loading -> {
                                    Text(
                                        text = "Carregando o arquivo de áudio...",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                is TranscriptionState.Transcribing -> {
                                    Text(
                                        text = "Enviando e transcrevendo... Isso pode levar um minuto para áudios grandes.",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                is TranscriptionState.Error -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Color.Red.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Erro",
                                            tint = Color.Red
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = (transcriptionState as TranscriptionState.Error).message,
                                            color = Color.Red,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                else -> {}
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Large Start Button
                    val isActionActive = transcriptionState !is TranscriptionState.Loading && 
                                         transcriptionState !is TranscriptionState.Transcribing
                    
                    Button(
                        onClick = { viewModel.startTranscription() },
                        enabled = isActionActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_transcription_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Iniciar Transcrição Inteligente",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // History Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Histórico de Transcrições",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${history.size} salvas",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (history.isEmpty()) {
            // Elegant Empty state
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sua biblioteca de transcrição está vazia.",
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Os áudios que sua mãe transcrever ficarão guardados com segurança aqui para leitura e exportação.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                history.forEach { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { onViewDetail(item.id) },
                        onDelete = { viewModel.deleteTranscription(item.id) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun HistoryItemCard(
    item: TranscriptionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir Transcrição?") },
            text = { Text("Tem certeza que deseja remover permanentemente a transcrição de \"${item.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Excluir", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("history_item_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.transcriptText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatDate(item.timestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatBytes(item.fileSize),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (item.modelUsed == "gemini-3.5-flash") "Flash" else "Pro",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.testTag("delete_item_button_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Deletar transcrição",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun DetailView(
    entity: TranscriptionEntity,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var showRenameDialog by remember { mutableStateOf(false) }

    // Audio Player State
    val audioUri = entity.audioUri
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var currentPosition by remember { mutableStateOf(0) }
    var audioInitError by remember { mutableStateOf<String?>(null) }

    fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    LaunchedEffect(audioUri) {
        // Release any existing player and reset position/states
        mediaPlayer?.let {
            try {
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayer = null
        }
        isPlaying = false
        currentPosition = 0

        if (!audioUri.isNullOrEmpty()) {
            try {
                val mp = withContext(Dispatchers.IO) {
                    android.media.MediaPlayer().apply {
                        val uri = Uri.parse(audioUri)
                        if (uri.scheme == "file" || uri.scheme == null) {
                            val path = uri.path ?: audioUri
                            val file = java.io.File(path)
                            if (file.exists()) {
                                setDataSource(file.absolutePath)
                            } else {
                                throw java.io.FileNotFoundException("Arquivo local não encontrado")
                            }
                        } else {
                            setDataSource(context, uri)
                        }
                        
                        setOnErrorListener { _, what, extra ->
                            isPlaying = false
                            audioInitError = "Erro na reprodução do áudio (código: $what, extra: $extra)"
                            true
                        }
                        
                        prepare()
                    }
                }
                mediaPlayer = mp
                duration = mp.duration
                audioInitError = null
            } catch (e: Exception) {
                e.printStackTrace()
                audioInitError = "O áudio original não pôde ser carregado. Ele pode ter sido movido, excluído ou estar sem permissão de acesso."
            }
        } else {
            audioInitError = "Nenhum arquivo de áudio associado a esta transcrição."
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                try {
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) {
                            currentPosition = mp.currentPosition
                        } else {
                            isPlaying = false
                        }
                    }
                } catch (e: Exception) {
                    isPlaying = false
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }

    LaunchedEffect(mediaPlayer) {
        mediaPlayer?.setOnCompletionListener {
            isPlaying = false
            currentPosition = 0
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    // Dialog for renaming
    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(entity.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Editar Nome da Transcrição", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Nome da Transcrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onRename(newTitle)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Document Metadata header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entity.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.size(28.dp).testTag("edit_title_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar Nome",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Arquivo: ${entity.fileName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Tamanho: ${formatBytes(entity.fileSize)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Data: ${formatDate(entity.timestamp)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Modelo: ${entity.modelUsed}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Audio Player Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Acompanhar Áudio Original",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                if (audioInitError != null) {
                    Text(
                        text = audioInitError ?: "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                try {
                                    mediaPlayer?.let { mp ->
                                        if (mp.isPlaying) {
                                            mp.pause()
                                            isPlaying = false
                                        } else {
                                            mp.start()
                                            isPlaying = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Erro ao controlar áudio", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                        ) {
                            if (isPlaying) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(width = 4.dp, height = 12.dp).background(MaterialTheme.colorScheme.onPrimary))
                                    Box(modifier = Modifier.size(width = 4.dp, height = 12.dp).background(MaterialTheme.colorScheme.onPrimary))
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Tocar",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { newValue ->
                                currentPosition = newValue.toInt()
                                try {
                                    mediaPlayer?.seekTo(newValue.toInt())
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            valueRange = 0f..(if (duration > 0) duration.toFloat() else 100f),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Actions panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(entity.transcriptText))
                    Toast.makeText(context, "Texto copiado para a Área de Transferência!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("copy_text_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copiar Texto", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, entity.transcriptText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Transcrição"))
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("share_text_button")
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Compartilhar", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Document Paper transcript body
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .widthIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Text(
                    text = entity.transcriptText,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("transcript_body_text")
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    provider: String,
    apiKey: String,
    openRouterApiKey: String,
    selectedModel: String,
    systemPrompt: String,
    openRouterPostProcessingEnabled: Boolean,
    openRouterPostProcessingModel: String,
    onSave: (String, String, String, String, String, Boolean, String) -> Unit,
    onRestorePrompt: () -> Unit
) {
    var providerState by remember { mutableStateOf(provider) }
    var keyState by remember { mutableStateOf(apiKey) }
    var openRouterKeyState by remember { mutableStateOf(openRouterApiKey) }
    var modelState by remember { mutableStateOf(selectedModel) }
    var promptState by remember { mutableStateOf(systemPrompt) }
    var openRouterPostProcessingEnabledState by remember { mutableStateOf(openRouterPostProcessingEnabled) }
    var openRouterPostProcessingModelState by remember { mutableStateOf(openRouterPostProcessingModel) }
    
    var showGeminiKey by remember { mutableStateOf(false) }
    var showOpenRouterKey by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()

    // Sync state if values change from viewmodel callbacks
    LaunchedEffect(provider, apiKey, openRouterApiKey, selectedModel, systemPrompt, openRouterPostProcessingEnabled, openRouterPostProcessingModel) {
        providerState = provider
        keyState = apiKey
        openRouterKeyState = openRouterApiKey
        modelState = selectedModel
        promptState = systemPrompt
        openRouterPostProcessingEnabledState = openRouterPostProcessingEnabled
        openRouterPostProcessingModelState = openRouterPostProcessingModel
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Provedor Selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Provedor de IA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { 
                            providerState = "gemini"
                            if (modelState != "gemini-3.5-flash" && modelState != "gemini-3.1-pro-preview") {
                                modelState = "gemini-3.5-flash"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (providerState == "gemini") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (providerState == "gemini") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Google Gemini", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { 
                            providerState = "openrouter"
                            val openRouterModels = listOf(
                                "mistralai/voxtral-mini-transcribe",
                                "microsoft/mai-transcribe-1.5",
                                "nvidia/parakeet-tdt-0.6b-v3",
                                "qwen/qwen3-asr-flash-2026-02-10",
                                "google/chirp-3",
                                "openai/whisper-large-v3-turbo"
                            )
                            if (!openRouterModels.contains(modelState)) {
                                modelState = "mistralai/voxtral-mini-transcribe"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (providerState == "openrouter") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (providerState == "openrouter") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("OpenRouter", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Credentials Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Credenciais de Acesso",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (providerState == "openrouter") {
                    OutlinedTextField(
                        value = openRouterKeyState,
                        onValueChange = { openRouterKeyState = it },
                        label = { Text("Chave de API do OpenRouter") },
                        placeholder = { Text("Cole sua OpenRouter API Key aqui...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("openrouter_key_field"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showOpenRouterKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (showOpenRouterKey) "Ocultar" else "Exibir",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { showOpenRouterKey = !showOpenRouterKey }
                                    .padding(end = 12.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Insira sua chave de API do OpenRouter para acessar os modelos de áudio. Ela é mantida apenas localmente no dispositivo.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    OutlinedTextField(
                        value = keyState,
                        onValueChange = { keyState = it },
                        label = { Text("Chave de API do Gemini") },
                        placeholder = { Text("Cole sua API Key aqui...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_field"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (showGeminiKey) "Ocultar" else "Exibir",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { showGeminiKey = !showGeminiKey }
                                    .padding(end = 12.dp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "O sistema utiliza a chave global padrão integrada se este campo for deixado em branco. Insira uma chave pessoal caso atinja limites de uso diários.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Model Selector Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Modelo Padrão",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                var expanded by remember { mutableStateOf(false) }
                val modelsList = if (providerState == "openrouter") {
                    listOf(
                        "mistralai/voxtral-mini-transcribe" to "Mistral Voxtral Mini",
                        "microsoft/mai-transcribe-1.5" to "Microsoft MAI-Transcribe 1.5",
                        "nvidia/parakeet-tdt-0.6b-v3" to "NVIDIA Parakeet v3",
                        "qwen/qwen3-asr-flash-2026-02-10" to "Qwen3 ASR Flash",
                        "google/chirp-3" to "Google Chirp 3",
                        "openai/whisper-large-v3-turbo" to "OpenAI Whisper Large v3 Turbo"
                    )
                } else {
                    listOf(
                        "gemini-3.5-flash" to "Gemini 3.5 Flash (Ultra-Rápido)",
                        "gemini-3.1-pro-preview" to "Gemini 3.1 Pro (Precisão Máxima)"
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    val activeLabel = modelsList.find { it.first == modelState }?.second ?: modelState
                    OutlinedTextField(
                        readOnly = true,
                        value = activeLabel,
                        onValueChange = {},
                        label = { Text("Modelo de Inteligência Artificial") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = OutlinedTextFieldDefaults.colors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("model_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        modelsList.forEach { pair ->
                            DropdownMenuItem(
                                text = { Text(pair.second) },
                                onClick = {
                                    modelState = pair.first
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (providerState == "openrouter") {
                        "Os modelos de áudio do OpenRouter são altamente otimizados para transcrições precisas em tempo recorde."
                    } else {
                        "O Gemini 3.5 Flash é ideal para a maioria das gravações por sua alta velocidade. Para gravações complexas, debates acalorados ou ruídos, selecione o Gemini 3.1 Pro."
                    },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (providerState == "openrouter") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .testTag("openrouter_post_processing_card"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pós-Processamento com LLM",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Usa uma IA para formatar e pontuar o áudio bruto baseado na instrução do prompt.",
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = openRouterPostProcessingEnabledState,
                            onCheckedChange = { openRouterPostProcessingEnabledState = it },
                            modifier = Modifier.testTag("post_processing_switch")
                        )
                    }

                    if (openRouterPostProcessingEnabledState) {
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        var postModelExpanded by remember { mutableStateOf(false) }
                        val postModelsList = listOf(
                            "nvidia/nemotron-3-ultra-550b-a55b:free" to "NVIDIA Nemotron 3 Ultra 550B (Gratuito)",
                            "openai/gpt-oss-120b:free" to "OpenAI GPT-OSS 120B (Gratuito)",
                            "nousresearch/hermes-3-llama-3.1-405b:free" to "Nous Hermes 3 Llama 3.1 405B (Gratuito)",
                            "deepseek/deepseek-v4-flash" to "DeepSeek v4 Flash"
                        )

                        ExposedDropdownMenuBox(
                            expanded = postModelExpanded,
                            onExpandedChange = { postModelExpanded = !postModelExpanded }
                        ) {
                            val activeLabel = postModelsList.find { it.first == openRouterPostProcessingModelState }?.second ?: openRouterPostProcessingModelState
                            OutlinedTextField(
                                readOnly = true,
                                value = activeLabel,
                                onValueChange = {},
                                label = { Text("Modelo de Pós-Processamento (LLM)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = postModelExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("post_model_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = postModelExpanded,
                                onDismissRequest = { postModelExpanded = false }
                            ) {
                                postModelsList.forEach { pair ->
                                    DropdownMenuItem(
                                        text = { Text(pair.second) },
                                        onClick = {
                                            openRouterPostProcessingModelState = pair.first
                                            postModelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // System Prompt Section (Always available, but Gemini-specific in logic or useful generally)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Instrução de Formatação (Prompt)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        "Restaurar Padrão",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onRestorePrompt() }
                            .padding(4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = promptState,
                    onValueChange = { promptState = it },
                    label = { Text("Instruções do Sistema") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("system_prompt_field"),
                    maxLines = 10,
                    colors = OutlinedTextFieldDefaults.colors()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Esse é o prompt que ensina ao Gemini como agir. Você pode personalizá-lo para que ele reconheça oradores específicos, use formatações parlamentares específicas da sua cidade ou siga outras regras especiais.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSave(providerState, keyState, openRouterKeyState, modelState, promptState, openRouterPostProcessingEnabledState, openRouterPostProcessingModelState) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("save_settings_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salvar Configurações", fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun SoundWaveVisual(isAnimating: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "sound_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (isAnimating) {
            // Draw 3 layers of wavy lines with phase offsets
            val path1 = Path()
            val path2 = Path()
            val path3 = Path()

            path1.moveTo(0f, centerY)
            path2.moveTo(0f, centerY)
            path3.moveTo(0f, centerY)

            for (x in 0..width.toInt() step 5) {
                val floatX = x.toFloat()
                // Sinusoidal heights modulated with boundaries
                val normalizedX = floatX / width
                val envelope = Math.sin(normalizedX * Math.PI).toFloat() // zero at edges, max at center

                val y1 = centerY + Math.sin(normalizedX * 4 * Math.PI + phase).toFloat() * 30f * envelope
                val y2 = centerY + Math.sin(normalizedX * 6 * Math.PI - phase * 1.2f).toFloat() * 20f * envelope
                val y3 = centerY + Math.cos(normalizedX * 5 * Math.PI + phase * 0.8f).toFloat() * 15f * envelope

                path1.lineTo(floatX, y1)
                path2.lineTo(floatX, y2)
                path3.lineTo(floatX, y3)
            }

            drawPath(path1, primaryColor, style = Stroke(width = 3.dp.toPx()))
            drawPath(path2, secondaryColor, style = Stroke(width = 2.dp.toPx()))
            drawPath(path3, primaryColor.copy(alpha = 0.05f), style = Stroke(width = 1.5.dp.toPx()))
        } else {
            // Draw a flat static/resting soundwave pattern
            drawLine(
                color = primaryColor,
                start = Offset(20f, centerY),
                end = Offset(width - 20f, centerY),
                strokeWidth = 2.dp.toPx()
            )
            // Some rhythmic pulses
            for (i in 0..10) {
                val progress = i / 10f
                val x = 40f + progress * (width - 80f)
                val lineH = if (i % 2 == 0) 15f else 8f
                drawLine(
                    color = primaryColor,
                    start = Offset(x, centerY - lineH),
                    end = Offset(x, centerY + lineH),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}


