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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    val messages = currentSession?.messages ?: emptyList()
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp)
            ) {
                // Drawer Header
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
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "GroqChat AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Formulas • Code • Diagrams",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        Text("New Chat")
                    }
                }

                HorizontalDivider()

                // Chat Sessions List
                Text(
                    text = "Recent Chats",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(sessions) { session ->
                        val isSelected = session.id == currentSessionId
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(
                                    text = session.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            badge = {
                                IconButton(
                                    onClick = { viewModel.deleteSession(session.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete chat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
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

                // Drawer Footer Settings Link
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings & API Key") },
                    selected = false,
                    onClick = {
                        viewModel.showSettingsDialog.value = true
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
                                Text(
                                    "GroqChat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = settings.selectedModel.split("-").take(2).joinToString(" "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = currentSession?.title ?: "Chat",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // Formula shortcut
                        IconButton(
                            onClick = { viewModel.insertFormulaSnippet() },
                            modifier = Modifier.testTag("shortcut_formula_button")
                        ) {
                            Icon(Icons.Default.Functions, contentDescription = "Insert formula")
                        }
                        // Settings
                        IconButton(
                            onClick = { viewModel.showSettingsDialog.value = true },
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        // Quick Suggestions / Action bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.insertFormulaSnippet() },
                                    label = { Text("Formula \$x^2\$", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(12.dp))
                                    },
                                    modifier = Modifier.padding(end = 6.dp).height(28.dp)
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.insertDiagramSnippet() },
                                    label = { Text("Mermaid", fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(12.dp))
                                    },
                                    modifier = Modifier.height(28.dp)
                                )
                            }

                            if (messages.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearCurrentSession() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Clear Chat", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Input Box Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speech Recognition Voice Button
                            IconButton(
                                onClick = {
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
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (isListeningSpeech) MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                                    .testTag("voice_input_button")
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
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_input_field"),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Send Button
                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = inputText.isNotBlank() && !isGenerating,
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                                    .testTag("send_message_button")
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (messages.isEmpty()) {
                    // Empty state prompt suggestions
                    EmptyChatSuggestions(
                        onSelectSuggestion = { suggestion ->
                            viewModel.sendMessage(suggestion)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatMessageItem(
                                message = message,
                                onExpandCode = { codeBlock ->
                                    viewModel.fullScreenCodeBlock.value = codeBlock
                                },
                                onExpandFormula = { formulaBlock ->
                                    viewModel.fullScreenFormulaBlock.value = formulaBlock
                                },
                                onExpandMermaid = { mermaidBlock ->
                                    viewModel.fullScreenMermaidBlock.value = mermaidBlock
                                },
                                onExpandSvg = { svgBlock ->
                                    viewModel.fullScreenSvgBlock.value = svgBlock
                                },
                                onSpeak = { text ->
                                    viewModel.speakText(text)
                                },
                                onRegenerate = {
                                    viewModel.regenerateLastResponse()
                                },
                                isSpeakingThis = speakingMessageContent == message.content
                            )
                        }
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

@Composable
fun EmptyChatSuggestions(
    onSelectSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "GroqChat AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Typeset Math • Full-Screen Code • Diagrams",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        val suggestions = listOf(
            "Explain quantum harmonic oscillators with formulas ${'$'}E_n = (n + \\frac{1}{2})\\hbar\\omega${'$'}",
            "Write a thread-safe LRU cache algorithm in Rust with tests",
            "Generate a Mermaid flowchart diagram of OAuth 2.0 PKCE flow",
            "Create an SVG vector badge for a cloud computing platform"
        )

        suggestions.forEach { prompt ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectSuggestion(prompt) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
