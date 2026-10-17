package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ContentBlock
import com.example.syntax.CodeHighlighter

private val SUPPORTED_LANGUAGES = listOf(
    "kotlin", "java", "python", "javascript", "typescript",
    "rust", "go", "c", "cpp", "csharp", "swift",
    "html", "xml", "css", "json", "sql", "bash",
    "yaml", "markdown", "ruby", "dart", "php", "mermaid"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenCodeViewerDialog(
    codeBlock: ContentBlock.CodeBlock,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showLineNumbers by remember { mutableStateOf(true) }
    var wordWrap by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableFloatStateOf(13f) }
    var activeLanguage by remember { mutableStateOf(codeBlock.language.lowercase()) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var copiedNotification by remember { mutableStateOf(false) }

    val lines = remember(codeBlock.code) { codeBlock.code.lines() }
    val lineCount = lines.size
    val charCount = codeBlock.code.length

    // Syntax highlight with search overlay
    val highlightedCode = remember(codeBlock.code, activeLanguage, searchQuery) {
        val base = CodeHighlighter.highlight(codeBlock.code, activeLanguage)
        if (searchQuery.isBlank()) {
            base
        } else {
            buildAnnotatedString {
                append(base)
                val queryLower = searchQuery.lowercase()
                val codeLower = codeBlock.code.lowercase()
                var startIndex = 0
                while (startIndex < codeLower.length) {
                    val found = codeLower.indexOf(queryLower, startIndex)
                    if (found == -1) break
                    addStyle(
                        SpanStyle(
                            background = Color(0xFFFACC15), // bright yellow highlight
                            color = Color(0xFF000000),
                            fontWeight = FontWeight.Bold
                        ),
                        found,
                        found + queryLower.length
                    )
                    startIndex = found + queryLower.length
                }
            }
        }
    }

    val matchCount = remember(codeBlock.code, searchQuery) {
        if (searchQuery.isBlank()) 0
        else {
            val q = searchQuery.lowercase()
            val text = codeBlock.code.lowercase()
            var count = 0
            var idx = 0
            while (idx < text.length) {
                val found = text.indexOf(q, idx)
                if (found == -1) break
                count++
                idx = found + q.length
            }
            count
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("full_screen_code_dialog"),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Box {
                                TextButton(
                                    onClick = { showLanguageMenu = true },
                                    modifier = Modifier.testTag("change_language_button")
                                ) {
                                    Text(
                                        text = activeLanguage.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch language",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                DropdownMenu(
                                    expanded = showLanguageMenu,
                                    onDismissRequest = { showLanguageMenu = false }
                                ) {
                                    SUPPORTED_LANGUAGES.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang.replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                activeLanguage = lang
                                                showLanguageMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_code_viewer")) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            // Search toggle
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Find",
                                    tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Line numbers toggle
                            IconButton(onClick = { showLineNumbers = !showLineNumbers }) {
                                Icon(
                                    Icons.Default.FormatListNumbered,
                                    contentDescription = "Toggle line numbers",
                                    tint = if (showLineNumbers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Word wrap toggle
                            IconButton(onClick = { wordWrap = !wordWrap }) {
                                Icon(
                                    Icons.Default.WrapText,
                                    contentDescription = "Toggle word wrap",
                                    tint = if (wordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Zoom controls
                            IconButton(onClick = { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(10f) }) {
                                Text("A-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { fontSizeSp = (fontSizeSp + 1f).coerceAtMost(22f) }) {
                                Text("A+", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }

                            // Copy
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(codeBlock.code))
                                copiedNotification = true
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy all")
                            }

                            // Share
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Code Snippet (${activeLanguage})")
                                    putExtra(Intent.EXTRA_TEXT, codeBlock.code)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Code"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Search Bar
                    AnimatedVisibility(visible = showSearch) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Find in code...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("search_code_input"),
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                if (searchQuery.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "$matchCount matches",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Metadata footer bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E222B))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$lineCount lines • $charCount characters • ${fontSizeSp.toInt()}sp font",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7F848E)
                        )
                        if (copiedNotification) {
                            Text(
                                text = "Copied to clipboard!",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            val vScrollState = rememberScrollState()
            val hScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF181A1F)) // sleek dark editor background
            ) {
                SelectionContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(vScrollState)
                            .then(if (!wordWrap) Modifier.horizontalScroll(hScrollState) else Modifier)
                            .padding(vertical = 12.dp)
                    ) {
                        // Line numbers gutter
                        if (showLineNumbers) {
                            Column(
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 12.dp)
                                    .widthIn(min = 36.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                lines.indices.forEach { index ->
                                    Text(
                                        text = "${index + 1}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSizeSp.sp,
                                        lineHeight = (fontSizeSp * 1.45f).sp,
                                        color = Color(0xFF5C6370),
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }

                            // Subtle vertical separator line
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(Color(0xFF2C313C))
                            )
                        }

                        // Code content
                        Text(
                            text = highlightedCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.45f).sp,
                            color = CodeHighlighter.DefaultTextColor,
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
