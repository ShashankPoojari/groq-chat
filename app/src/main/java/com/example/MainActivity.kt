package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ChatMessage
import com.example.model.MessageRole
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.wordprocessor.WordProcessorLayout
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GroqChatAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroqChatAppScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsStateWithLifecycle()

    // Full screen viewer states
    val fullScreenCode by viewModel.fullScreenCodeBlock.collectAsStateWithLifecycle()
    val fullScreenFormula by viewModel.fullScreenFormulaBlock.collectAsStateWithLifecycle()
    val fullScreenMermaid by viewModel.fullScreenMermaidBlock.collectAsStateWithLifecycle()
    val fullScreenSvg by viewModel.fullScreenSvgBlock.collectAsStateWithLifecycle()

    val isListeningSpeech by viewModel.isListeningSpeech.collectAsStateWithLifecycle()
    val speakingMessageContent by viewModel.speakingMessageContent.collectAsStateWithLifecycle()

    // Layout mode: Word Processor (Default) vs Messenger Chat View
    var useWordProcessorLayout by remember { mutableStateOf(true) }

    // Speech Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleSpeechRecognition()
        } else {
            viewModel.errorMessage.value = "Microphone permission required for voice prompt"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (useWordProcessorLayout) {
            // Famous Word Processor Layout (Office / Google Docs style Paper Document & Ribbon)
            WordProcessorLayout(
                currentSession = currentSession,
                sessions = sessions,
                settings = settings,
                isGenerating = isGenerating,
                inputText = inputText,
                onInputTextChange = { viewModel.inputText.value = it },
                onSendMessage = { explicit -> viewModel.sendMessage(explicit) },
                onSelectSession = { viewModel.selectSession(it) },
                onNewDocument = { viewModel.createNewSession() },
                onDeleteDocument = { viewModel.deleteSession(it) },
                onOpenSettings = { viewModel.showSettingsDialog.value = true },
                onExpandCode = { viewModel.fullScreenCodeBlock.value = it },
                onExpandFormula = { viewModel.fullScreenFormulaBlock.value = it },
                onExpandMermaid = { viewModel.fullScreenMermaidBlock.value = it },
                onExpandSvg = { viewModel.fullScreenSvgBlock.value = it },
                onToggleSpeech = {
                    val hasRecordPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasRecordPermission) {
                        viewModel.toggleSpeechRecognition()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                isListeningSpeech = isListeningSpeech,
                onSpeakText = { viewModel.speakText(it) }
            )
        } else {
            // Alternative Messenger Chat Layout
            MessengerChatLayout(
                viewModel = viewModel,
                currentSession = currentSession,
                sessions = sessions,
                currentSessionId = currentSessionId,
                settings = settings,
                inputText = inputText,
                isGenerating = isGenerating,
                isListeningSpeech = isListeningSpeech,
                speakingMessageContent = speakingMessageContent,
                onToggleSpeech = {
                    val hasRecordPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasRecordPermission) {
                        viewModel.toggleSpeechRecognition()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onSwitchToWordProcessor = { useWordProcessorLayout = true }
            )
        }

        // Floating Layout Switcher Chip (allows toggling between Word Processor & Chat at any time)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 96.dp, end = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { useWordProcessorLayout = !useWordProcessorLayout }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (useWordProcessorLayout) Icons.Default.Chat else Icons.Default.Description,
                    contentDescription = "Toggle View",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (useWordProcessorLayout) "Chat View" else "Doc View",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Error notification banner
        errorMessage?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.errorMessage.value = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Full-Screen Dialogs
    fullScreenCode?.let { codeBlock ->
        FullScreenCodeViewerDialog(
            codeBlock = codeBlock,
            onDismiss = { viewModel.fullScreenCodeBlock.value = null }
        )
    }

    fullScreenFormula?.let { formulaBlock ->
        FullScreenFormulaDialog(
            formula = formulaBlock,
            onDismiss = { viewModel.fullScreenFormulaBlock.value = null }
        )
    }

    fullScreenMermaid?.let { mermaidBlock ->
        FullScreenMermaidDialog(
            block = mermaidBlock,
            onDismiss = { viewModel.fullScreenMermaidBlock.value = null }
        )
    }

    fullScreenSvg?.let { svgBlock ->
        FullScreenSvgDialog(
            block = svgBlock,
            onDismiss = { viewModel.fullScreenSvgBlock.value = null }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settings,
            onSaveSettings = { updated ->
                viewModel.updateSettings(updated)
            },
            onDismiss = { viewModel.showSettingsDialog.value = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerChatLayout(
    viewModel: ChatViewModel,
    currentSession: com.example.model.ChatSession?,
    sessions: List<com.example.model.ChatSession>,
    currentSessionId: String,
    settings: com.example.model.AppSettings,
    inputText: String,
    isGenerating: Boolean,
    isListeningSpeech: Boolean,
    speakingMessageContent: String?,
    onToggleSpeech: () -> Unit,
    onSwitchToWordProcessor: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val messages = currentSession?.messages ?: emptyList()

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(310.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("GroqChat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Document & Chat Studio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.createNewSession()
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("new_chat_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Document / Chat")
                    }
                }

                HorizontalDivider()

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(sessions) { session ->
                        val isSelected = session.id == currentSessionId
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Description, contentDescription = null) },
                            label = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            badge = {
                                IconButton(
                                    onClick = { viewModel.deleteSession(session.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                }
                            },
                            selected = isSelected,
                            onClick = {
                                viewModel.selectSession(session.id)
                                coroutineScope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }

                HorizontalDivider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                    label = { Text("Word Processor View") },
                    selected = false,
                    onClick = {
                        onSwitchToWordProcessor()
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("GroqChat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(settings.selectedModel.split("-").take(2).joinToString(" "), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(currentSession?.title ?: "Document", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.showSettingsDialog.value = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onToggleSpeech,
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(if (isListeningSpeech) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isListeningSpeech) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Voice prompt",
                                    tint = if (isListeningSpeech) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { viewModel.inputText.value = it },
                                placeholder = { Text("Ask anything (supports \$math\$, code, diagrams)...") },
                                maxLines = 4,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = inputText.isNotBlank() && !isGenerating,
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            onExpandCode = { viewModel.fullScreenCodeBlock.value = it },
                            onExpandFormula = { viewModel.fullScreenFormulaBlock.value = it },
                            onExpandMermaid = { viewModel.fullScreenMermaidBlock.value = it },
                            onExpandSvg = { viewModel.fullScreenSvgBlock.value = it },
                            onSpeak = { viewModel.speakText(it) },
                            onRegenerate = { viewModel.regenerateLastResponse() },
                            isSpeakingThis = speakingMessageContent == message.content
                        )
                    }
                }
            }
        }
    }
}
