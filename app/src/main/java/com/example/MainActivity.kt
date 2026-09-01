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
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.example.data.SegmentUtils
import com.example.data.TimedParagraph
import com.example.data.TranscriptionEntity
import com.example.data.provider.ModelCatalog
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            Screen.Dashboard -> "Taquigrafia Pro"
                            Screen.Detail -> "Visualizar Transcrição"
                            Screen.Settings -> "Configurações"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (currentScreen == Screen.Dashboard) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    if (currentScreen != Screen.Dashboard) {
                        IconButton(onClick = { currentScreen = Screen.Dashboard }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = if (currentScreen == Screen.Dashboard) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = if (currentScreen == Screen.Dashboard) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = if (currentScreen == Screen.Dashboard) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
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
                                },
                                onUpdateText = { newText ->
                                    viewModel.updateTranscriptText(entity.id, newText)
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

@OptIn(ExperimentalLayoutApi::class)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(MaterialTheme.shapes.large)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
        ) {
            val isTranscribing = transcriptionState is TranscriptionState.Transcribing ||
                transcriptionState is TranscriptionState.Loading
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp).clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            ) {
                SoundWaveVisual(isAnimating = isTranscribing)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp).padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Transcrição de Áudios de Plenário",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gere textos fidedignos e formatados instantaneamente",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nova Transcrição",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedFile == null) {
                    // Empty Picker Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                shape = MaterialTheme.shapes.medium
                            )
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Suporta MP3, WAV, M4A, OGG, AAC, etc.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Selected File Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = MaterialTheme.shapes.medium
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
                                    MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                )
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFile!!.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = formatBytes(selectedFile!!.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AssistChip(
                                    onClick = {},
                                    label = { Text(selectedFile!!.mimeType.substringAfter("/").uppercase(), style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, labelColor = MaterialTheme.colorScheme.onSecondaryContainer)
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    run {
                        val providerLabel = if (currentProvider == "openrouter") "OpenRouter" else "Gemini"
                        val modelShort = ModelCatalog.shortLabel(currentModel)
                        val hasKey = viewModel.hasEffectiveKey(currentProvider)
                        val isCustomKey = if (currentProvider == "openrouter") currentOpenRouterApiKey.trim().isNotEmpty() else currentApiKey.trim().isNotEmpty()
                        val statusLabel = when { isCustomKey -> "Chave ativa"; hasKey -> "Chave padrão"; else -> "Sem chave" }
                        val chipColor = when { isCustomKey || hasKey -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer; else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer }
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Provedor: $providerLabel • $modelShort", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, labelColor = MaterialTheme.colorScheme.onSecondaryContainer, leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text(statusLabel, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(if (isCustomKey || hasKey) Icons.Default.Check else Icons.Default.Warning, null, modifier = Modifier.size(14.dp)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = chipColor.first, labelColor = chipColor.second, leadingIconContentColor = chipColor.second)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = transcriptionState !is TranscriptionState.Idle) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            when (transcriptionState) {
                                is TranscriptionState.Loading -> {
                                    Text(
                                        text = "Carregando o arquivo de áudio...",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                is TranscriptionState.Transcribing -> {
                                    Text(
                                        text = "Enviando e transcrevendo... Isso pode levar um minuto para áudios grandes.",
                                        style = MaterialTheme.typography.labelLarge,
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
                                                MaterialTheme.colorScheme.errorContainer,
                                                shape = MaterialTheme.shapes.small
                                            )
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Erro",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = (transcriptionState as TranscriptionState.Error).message,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                else -> {}
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    val isActionActive = transcriptionState !is TranscriptionState.Loading &&
                        transcriptionState !is TranscriptionState.Transcribing

                    Button(
                        onClick = { viewModel.startTranscription() },
                        enabled = isActionActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_transcription_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (!isActionActive) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            "Iniciar Transcrição Inteligente",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Histórico de Transcrições",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (history.size == 1) "1 transcrição salva" else "${history.size} transcrições salvas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (history.isEmpty()) {
            // Elegant Empty state
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sua biblioteca de transcrição está vazia.",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Os áudios que sua mãe transcrever ficarão guardados com segurança aqui para leitura e exportação.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalLayoutApi::class)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("history_item_${item.id}"),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.transcriptText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    verticalArrangement = Arrangement.Center,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatDate(item.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatBytes(item.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(ModelCatalog.shortLabel(item.modelUsed), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, labelColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.testTag("delete_item_button_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Deletar transcrição",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailView(
    entity: TranscriptionEntity,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateText: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showRenameDialog by remember { mutableStateOf(false) }

    // Audio Player State
    val audioUri = entity.audioUri
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(entity.audioDurationMs ?: 0) }
    var currentPosition by remember { mutableStateOf(0) }
    var audioInitError by remember { mutableStateOf<String?>(null) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    LaunchedEffect(audioUri) {
        mediaPlayer?.let { try { it.release() } catch (e: Exception) { e.printStackTrace() }; mediaPlayer = null }
        isPlaying = false
        currentPosition = 0
        duration = entity.audioDurationMs ?: 0
        audioInitError = null

        if (!audioUri.isNullOrEmpty()) {
            try {
                val mp = withContext(Dispatchers.IO) {
                    android.media.MediaPlayer().apply {
                        setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        val uri = Uri.parse(audioUri)
                        if (uri.scheme == "file" || uri.scheme == null) {
                            val path = uri.path ?: audioUri
                            val file = java.io.File(path)
                            if (!file.exists()) throw java.io.FileNotFoundException("Arquivo local não encontrado: $path")
                            setDataSource(file.absolutePath)
                        } else {
                            setDataSource(context, uri)
                        }
                        setOnErrorListener { _, what, extra ->
                            isPlaying = false
                            audioInitError = if (what == 1 && extra == -2147483648) "Emulador sem saída de áudio (-no-audio). O arquivo existe, mas a reprodução só funciona em aparelho real."
                            else "Erro na reprodução do áudio (código: $what, extra: $extra)"
                            true
                        }
                        setOnCompletionListener { isPlaying = false; currentPosition = 0 }
                        prepare()
                    }
                }
                mediaPlayer = mp
                try {
                    val mpDur = mp.duration
                    if (mpDur > 500) duration = mpDur
                } catch (_: Exception) {}
            } catch (e: Exception) {
                e.printStackTrace()
                audioInitError = "O áudio original não pôde ser carregado (${e.javaClass.simpleName}: ${e.localizedMessage}). No emulador headless com -no-audio a reprodução falha, mas o arquivo está salvo."
            }
        } else {
            audioInitError = "Nenhum arquivo de áudio associado a esta transcrição."
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer, isUserSeeking) {
        if (isUserSeeking) { kotlinx.coroutines.delay(120); return@LaunchedEffect }
        while (isPlaying && !isUserSeeking) {
            val mp = mediaPlayer
            if (mp == null) { isPlaying = false; break }
            try {
                val playing = try { mp.isPlaying } catch (_: IllegalStateException) { isPlaying = false; break }
                if (playing) {
                    currentPosition = try { mp.currentPosition } catch (_: Exception) { currentPosition }
                } else {
                    isPlaying = false; break
                }
            } catch (_: IllegalStateException) { isPlaying = false; break }
            catch (_: Exception) { break }
            kotlinx.coroutines.delay(50)
        }
    }

    DisposableEffect(Unit) {
        onDispose { try { mediaPlayer?.release() } catch (_: Exception) {} }
    }

    val segments = remember(entity.segmentsJson) {
        entity.segmentsJson?.let { json ->
            try {
                val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.api.Segment::class.java)
                @Suppress("UNCHECKED_CAST")
                val adapter = moshi.adapter<List<com.example.data.api.Segment>>(type)
                adapter.fromJson(json)?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) { null }
        }
    }
    val rawParagraphs = remember(entity.transcriptText) { SegmentUtils.splitParagraphs(entity.transcriptText) }
    val editableParas = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    LaunchedEffect(entity.transcriptText) {
        val fresh = SegmentUtils.splitParagraphs(entity.transcriptText)
        editableParas.clear()
        editableParas.addAll(fresh)
        hasUnsavedChanges = false
    }

    val displayParas: List<String> = remember(rawParagraphs, editableParas.toList()) {
        if (editableParas.isNotEmpty()) editableParas.toList() else rawParagraphs
    }

    val effectiveAudioDuration = remember(duration, entity.audioDurationMs) {
        entity.audioDurationMs?.takeIf { it > 500 } ?: duration.takeIf { it > 500 }
    }

    val timedParagraphs: List<TimedParagraph> = remember(displayParas, segments, effectiveAudioDuration) {
        SegmentUtils.buildTimedParagraphs(displayParas, segments, effectiveAudioDuration)
    }

    val activeIdx by remember {
        derivedStateOf {
            SegmentUtils.findActiveIndex(currentPosition, timedParagraphs)
        }
    }

    var isExpanded by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    val listState = rememberLazyListState()

    LaunchedEffect(activeIdx, isPlaying, isExpanded) {
        if (isPlaying && !isExpanded && timedParagraphs.isNotEmpty() && !listState.isScrollInProgress) {
            val visible = try { listState.layoutInfo.visibleItemsInfo } catch (_: Exception) { emptyList() }
            val firstVisible = visible.firstOrNull()?.index ?: -1
            val lastVisible = visible.lastOrNull()?.index ?: -1
            val outOfView = firstVisible == -1 || activeIdx <= firstVisible || activeIdx >= lastVisible
            if (outOfView) {
                try {
                    listState.animateScrollToItem(
                        index = (activeIdx - 1).coerceAtLeast(0),
                        scrollOffset = 0
                    )
                } catch (_: Exception) {}
            }
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
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var headerExpanded by remember { mutableStateOf(false) }
        if (!isExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entity.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false).clickable { showRenameDialog = true }
                    )
                    IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(22.dp).testTag("edit_title_button")) {
                        Icon(Icons.Default.Edit, "Editar Nome", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = "${formatBytes(entity.fileSize)} • ${formatTime(duration)} • ${ModelCatalog.shortLabel(entity.modelUsed)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { headerExpanded = !headerExpanded }, modifier = Modifier.size(28.dp)) {
                Text(text = if (headerExpanded) "▴" else "▾", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(visible = headerExpanded, modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)) {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Arquivo: ${entity.fileName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Data: ${formatDate(entity.timestamp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Modelo: ${entity.modelUsed}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }

        if (!isExpanded) Spacer(modifier = Modifier.height(8.dp))

        if (!isExpanded || !isImeVisible) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Acompanhar Áudio Original",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                if (audioInitError != null) {
                    Text(
                        text = audioInitError ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val mp = mediaPlayer
                                if (mp == null) {
                                    Toast.makeText(context, audioInitError ?: "Áudio não carregado (emulador sem áudio: -no-audio). Arquivo salvo em disco.", Toast.LENGTH_LONG).show()
                                    return@IconButton
                                }
                                try {
                                    val playing = try { mp.isPlaying } catch (_: IllegalStateException) { false }
                                    if (playing) { try { mp.pause() } catch (_: Exception) {}; isPlaying = false }
                                    else { try { mp.start(); isPlaying = true } catch (e: Exception) { e.printStackTrace(); Toast.makeText(context, "Falha ao iniciar áudio: ${e.message} (emulador foi iniciado com -no-audio)", Toast.LENGTH_LONG).show() } }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Erro ao controlar áudio: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            value = if (sliderDragging) dragValue else currentPosition.toFloat(),
                            onValueChange = { newValue ->
                                sliderDragging = true
                                isUserSeeking = true
                                dragValue = newValue
                                currentPosition = newValue.toInt()
                            },
                            onValueChangeFinished = {
                                sliderDragging = false
                                isUserSeeking = false
                                currentPosition = dragValue.toInt()
                                try { mediaPlayer?.seekTo(dragValue.toInt()) } catch (e: Exception) { e.printStackTrace() }
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
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }

        if (!isExpanded) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(entity.transcriptText))
                    Toast.makeText(context, "Texto copiado!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp).testTag("copy_text_button")
            ) { Icon(Icons.Default.Check, "Copiar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            IconButton(
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, entity.transcriptText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Transcrição"))
                },
                modifier = Modifier.size(32.dp).testTag("share_text_button")
            ) { Icon(Icons.Default.Share, "Compartilhar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        } else if (!isImeVisible) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        fun persistEdits() {
            val joined = editableParas.joinToString("\n\n")
            onUpdateText(joined)
            hasUnsavedChanges = false
            Toast.makeText(context, "Transcrição salva!", Toast.LENGTH_SHORT).show()
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .widthIn(max = 600.dp)
                .then(if (isExpanded && isImeVisible) Modifier.padding(bottom = 4.dp) else Modifier),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isExpanded && isImeVisible) 0.dp else 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (isExpanded && isImeVisible) 4.dp else 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Modo Edição" else "Transcrição",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isExpanded) {
                            if (hasUnsavedChanges) {
                                TextButton(
                                    onClick = { persistEdits() },
                                    modifier = Modifier.testTag("save_edit_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("Salvar", fontSize = 12.sp) }
                            }
                            IconButton(
                                onClick = {
                                    if (hasUnsavedChanges) persistEdits()
                                    isExpanded = false
                                },
                                modifier = Modifier.size(28.dp).testTag("expand_text_button")
                            ) {
                                Icon(Icons.Default.Close, "Fechar edição", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            IconButton(
                                onClick = { isExpanded = true },
                                modifier = Modifier.size(32.dp).testTag("expand_text_button")
                            ) {
                                Icon(Icons.Default.Edit, "Expandir e editar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (isExpanded) {
                    val scrollState = rememberScrollState()
                    val highlightBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
                    fun buildEditAnnotated(paras: List<String>, active: Int): AnnotatedString {
                        return androidx.compose.ui.text.buildAnnotatedString {
                            paras.forEachIndexed { i, para ->
                                if (i > 0) append("\n\n")
                                val start = length
                                append(para)
                                val end = length
                                if (i == active) {
                                    addStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            background = highlightBg,
                                            color = highlightFg
                                        ), start, end
                                    )
                                }
                            }
                        }
                    }
                    var editFieldValue by remember {
                        mutableStateOf(
                            androidx.compose.ui.text.input.TextFieldValue(
                                annotatedString = buildEditAnnotated(editableParas.toList(), activeIdx)
                            )
                        )
                    }
                    LaunchedEffect(editableParas.toList(), activeIdx) {
                        val annotated = buildEditAnnotated(editableParas.toList(), activeIdx)
                        val currentText = editFieldValue.text
                        if (annotated.text != currentText) {
                            val sel = editFieldValue.selection
                            val newSel = when {
                                currentText.isEmpty() -> androidx.compose.ui.text.TextRange(annotated.length)
                                sel.start > annotated.length || sel.end > annotated.length -> androidx.compose.ui.text.TextRange(annotated.length)
                                else -> sel
                            }
                            editFieldValue = editFieldValue.copy(annotatedString = annotated, selection = newSel)
                        } else if (editFieldValue.annotatedString != annotated) {
                            editFieldValue = editFieldValue.copy(annotatedString = annotated)
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp).testTag("transcript_body_text")
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalTextSelectionColors provides TextSelectionColors(
                                handleColor = MaterialTheme.colorScheme.primary,
                                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            )
                        ) {
                            BasicTextField(
                                value = editFieldValue,
                                onValueChange = { newValue ->
                                    val raw = newValue.text
                                    val paras = if (raw.isEmpty()) listOf("") else raw.split("\n\n")
                                    editableParas.clear()
                                    editableParas.addAll(paras)
                                    hasUnsavedChanges = true
                                    val annotated = buildEditAnnotated(paras, activeIdx)
                                    val sel = newValue.selection
                                    val clampedSel = androidx.compose.ui.text.TextRange(
                                        sel.start.coerceIn(0, annotated.length),
                                        sel.end.coerceIn(0, annotated.length)
                                    )
                                    editFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                        annotatedString = annotated,
                                        selection = clampedSel,
                                        composition = newValue.composition
                                    )
                                },
                                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).testTag("expanded_text_field"),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.SansSerif, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField -> Box(modifier = Modifier.fillMaxWidth()) { innerTextField() } }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("transcript_body_text"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(timedParagraphs) { idx, para ->
                            val isActive = idx == activeIdx
                            val bg = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent
                            val tc = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            Text(
                                text = para.text,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = tc,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable {
                                        try {
                                            val targetMs = para.startMs
                                            currentPosition = targetMs
                                            mediaPlayer?.seekTo(targetMs)
                                            val mp = mediaPlayer
                                            if (mp != null && !isPlaying) {
                                                try {
                                                    mp.start()
                                                    isPlaying = true
                                                } catch (_: Exception) {}
                                            }
                                        } catch (_: Exception) {
                                            currentPosition = para.startMs
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("paragraph_$idx")
                            )
                        }
                    }
                }
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
                            if (ModelCatalog.geminiModels.none { it.id == modelState }) {
                                modelState = ModelCatalog.geminiModels.first().id
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
                            if (ModelCatalog.openRouterTranscriptionModels.none { it.id == modelState }) {
                                modelState = ModelCatalog.openRouterTranscriptionModels.first().id
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
                        text = "Se deixar em branco e houver chave de build (OPENROUTER_API_KEY), ela será usada. Caso contrário, configure aqui.",
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
                        text = "Se deixar em branco e houver chave de build (GEMINI_API_KEY), ela será usada. Caso contrário, configure aqui.",
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
                    ModelCatalog.openRouterTranscriptionModels.map { it.id to it.label }
                } else {
                    ModelCatalog.geminiModels.map { it.id to it.label }
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
                                "Desativado por padrão para máxima fidelidade. Quando ativo, corrige pontuação/ortografia com LLM (temp. 0.1) — revise sempre; pode alucinar em silêncio/ruído.",
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
                        val postModelsList = ModelCatalog.openRouterPostProcessingModels.map { it.id to it.label }

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
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)

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
            for (i in 0..6) {
                val progress = i / 6f
                val x = 40f + progress * (width - 80f)
                val lineH = if (i % 2 == 0) 10f else 6f
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


