package com.example.ui.wordprocessor

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.parser.MarkdownParser
import com.example.ui.components.*
import kotlinx.coroutines.launch

enum class WordProcessorViewMode {
    PRINT_LAYOUT,   // Paper Sheet with margins and shadows (Word / Docs default)
    WEB_LAYOUT,     // Continuous pageless flow
    DOCUMENT_SOURCE // Raw Markdown / Text editing mode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordProcessorLayout(
    currentSession: ChatSession?,
    sessions: List<ChatSession>,
    settings: AppSettings,
    isGenerating: Boolean,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    onSelectSession: (String) -> Unit,
    onNewDocument: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onExpandCode: (ContentBlock.CodeBlock) -> Unit,
    onExpandFormula: (ContentBlock.FormulaBlock) -> Unit,
    onExpandMermaid: (ContentBlock.MermaidBlock) -> Unit,
    onExpandSvg: (ContentBlock.SvgBlock) -> Unit,
    onToggleSpeech: () -> Unit,
    isListeningSpeech: Boolean,
    onSpeakText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Word Processor State
    var viewMode by remember { mutableStateOf(WordProcessorViewMode.PRINT_LAYOUT) }
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var showOutlineDrawer by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf<String?>(null) } // "File", "Edit", "View", "Insert", "Format", "Tools"
    var showCopilotDock by remember { mutableStateOf(true) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Active formatting toggles in ribbon
    var isBold by remember { mutableStateOf(false) }
    var isItalic by remember { mutableStateOf(false) }
    var isUnderline by remember { mutableStateOf(false) }
    var isStrikethrough by remember { mutableStateOf(false) }
    var selectedFontFamily by remember { mutableStateOf("Sans-Serif") }
    var selectedFontSizePt by remember { mutableIntStateOf(12) }
    var selectedStyle by remember { mutableStateOf("Normal text") }
    var textAlign by remember { mutableStateOf(TextAlign.Start) }

    // Combined document text from session messages
    val documentTitle = currentSession?.title ?: "Untitled Document"
    val messages = currentSession?.messages ?: emptyList()
    val fullDocumentContent = remember(messages) {
        if (messages.isEmpty()) ""
        else messages.joinToString("\n\n") { it.content }
    }

    // Parse blocks for structured rendering
    val documentBlocks = remember(fullDocumentContent) {
        MarkdownParser.parse(fullDocumentContent)
    }

    // Word and character counts
    val wordCount = remember(fullDocumentContent) {
        if (fullDocumentContent.isBlank()) 0
        else fullDocumentContent.trim().split(Regex("\\s+")).size
    }
    val charCount = fullDocumentContent.length

    // Document headings for table of contents
    val headings = remember(fullDocumentContent) {
        fullDocumentContent.lines()
            .filter { it.startsWith("#") }
            .map { line ->
                val level = line.takeWhile { it == '#' }.length
                val title = line.removePrefix("#".repeat(level)).trim()
                Pair(level, title)
            }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                DocumentNavigationPane(
                    documentTitle = documentTitle,
                    wordCount = wordCount,
                    charCount = charCount,
                    headings = headings,
                    sessions = sessions,
                    currentSessionId = currentSession?.id ?: "",
                    onSelectSession = {
                        onSelectSession(it)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewDocument = {
                        onNewDocument()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onDeleteDocument = onDeleteDocument,
                    onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    // 1. Google Docs / Word Style Header & Menu Bar
                    WordProcessorTopHeader(
                        documentTitle = documentTitle,
                        isSaved = !isGenerating,
                        onOpenNavigation = { coroutineScope.launch { drawerState.open() } },
                        onOpenSettings = onOpenSettings,
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "$documentTitle.docx")
                                putExtra(Intent.EXTRA_TEXT, fullDocumentContent)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Document"))
                        },
                        onExport = { showExportDialog = true },
                        showMenuDropdown = showMenuDropdown,
                        onMenuClick = { showMenuDropdown = if (showMenuDropdown == it) null else it },
                        onDismissMenu = { showMenuDropdown = null },
                        onInsertEquation = { onSendMessage("Insert mathematical equation") },
                        onInsertDiagram = { onSendMessage("Insert Mermaid diagram") },
                        onInsertCode = { onSendMessage("Insert code snippet") },
                        onInsertSvg = { onSendMessage("Insert SVG graphic") },
                        onClearDocument = { onSendMessage(null) },
                        onViewModeChange = { viewMode = it }
                    )

                    // 2. Famous Word Processor Ribbon Toolbar
                    WordProcessorRibbon(
                        selectedStyle = selectedStyle,
                        onStyleChange = { selectedStyle = it },
                        fontFamily = selectedFontFamily,
                        onFontFamilyChange = { selectedFontFamily = it },
                        fontSizePt = selectedFontSizePt,
                        onFontSizeChange = { selectedFontSizePt = it },
                        isBold = isBold,
                        onBoldToggle = { isBold = !isBold },
                        isItalic = isItalic,
                        onItalicToggle = { isItalic = !isItalic },
                        isUnderline = isUnderline,
                        onUnderlineToggle = { isUnderline = !isUnderline },
                        isStrikethrough = isStrikethrough,
                        onStrikethroughToggle = { isStrikethrough = !isStrikethrough },
                        textAlign = textAlign,
                        onTextAlignChange = { textAlign = it },
                        onInsertEquation = {
                            onInputTextChange(if (inputText.isBlank()) "Typeset the formula \$f(x) = \\int_{-\\infty}^{\\infty} e^{-x^2} dx\$" else "$inputText \$x^2\$")
                        },
                        onInsertDiagram = {
                            onInputTextChange(if (inputText.isBlank()) "Generate a Mermaid flowchart diagram" else "$inputText \nGenerate a Mermaid diagram")
                        },
                        onInsertSvg = {
                            onInputTextChange(if (inputText.isBlank()) "Create an SVG graphic illustration" else "$inputText \nCreate an SVG vector graphic")
                        },
                        onInsertCode = {
                            onInputTextChange(if (inputText.isBlank()) "Write a Kotlin algorithm with syntax highlighting" else "$inputText \nWrite code in Kotlin")
                        },
                        onCopilotPrompt = { showCopilotDock = !showCopilotDock }
                    )

                    // 3. Document Ruler (Signature Word / Google Docs Feature)
                    WordProcessorRuler(zoomLevel = zoomLevel)
                }
            },
            bottomBar = {
                Column {
                    // Floating / Docked Word Copilot Prompt Pill
                    AnimatedVisibility(visible = showCopilotDock) {
                        WordProcessorCopilotDock(
                            inputText = inputText,
                            onInputTextChange = onInputTextChange,
                            onSendMessage = onSendMessage,
                            isGenerating = isGenerating,
                            isListeningSpeech = isListeningSpeech,
                            onToggleSpeech = onToggleSpeech,
                            selectedModel = settings.selectedModel
                        )
                    }

                    // Bottom Word Processor Status Bar (Page, Word count, Zoom slider)
                    WordProcessorStatusBar(
                        wordCount = wordCount,
                        charCount = charCount,
                        viewMode = viewMode,
                        onViewModeChange = { viewMode = it },
                        zoomLevel = zoomLevel,
                        onZoomChange = { zoomLevel = it.coerceIn(0.75f, 1.5f) }
                    )
                }
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            // Document Canvas (Paper Desk Workspace)
            val deskBackground = if (isSystemInDarkTheme()) Color(0xFF1E2024) else Color(0xFFE8EAED)
            val sheetBackground = if (isSystemInDarkTheme()) Color(0xFF2B2D31) else Color(0xFFFFFFFF)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(deskBackground)
            ) {
                val listState = rememberLazyListState()

                if (fullDocumentContent.isBlank()) {
                    // Empty Document Starter Sheet
                    EmptyDocumentSheet(
                        onSelectTemplate = { templatePrompt ->
                            onSendMessage(templatePrompt)
                        },
                        sheetBackground = sheetBackground,
                        zoomLevel = zoomLevel
                    )
                } else {
                    // Paper Document Canvas with margins and page elevation
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = if (viewMode == WordProcessorViewMode.PRINT_LAYOUT) 16.dp else 4.dp,
                                    vertical = 12.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                // Realistic Paper Sheet Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 840.dp)
                                        .graphicsLayer(scaleX = zoomLevel, scaleY = zoomLevel)
                                        .then(
                                            if (viewMode == WordProcessorViewMode.PRINT_LAYOUT) {
                                                Modifier
                                                    .shadow(
                                                        elevation = 4.dp,
                                                        shape = RoundedCornerShape(2.dp),
                                                        spotColor = Color(0x33000000)
                                                    )
                                                    .border(0.5.dp, Color(0x22000000), RoundedCornerShape(2.dp))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .background(sheetBackground, RoundedCornerShape(2.dp))
                                        .padding(
                                            horizontal = if (viewMode == WordProcessorViewMode.PRINT_LAYOUT) 32.dp else 16.dp,
                                            vertical = 36.dp
                                        )
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Header / Document Title on Page
                                        Text(
                                            text = documentTitle.replace(".docx", ""),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = textAlign,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Render Structured Blocks (Text with inline formulas, Code, Formulas, Mermaid, SVG, Tables)
                                        documentBlocks.forEach { block ->
                                            when (block) {
                                                is ContentBlock.TextBlock -> {
                                                    RenderTextBlockWithInlineFormulas(
                                                        block = block,
                                                        isUser = false,
                                                        onExpandFormula = { latex ->
                                                            onExpandFormula(ContentBlock.FormulaBlock(formula = latex, isDisplayMode = false))
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                                is ContentBlock.CodeBlock -> {
                                                    RenderCodeBlockCard(
                                                        codeBlock = block,
                                                        onExpand = { onExpandCode(block) }
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                                is ContentBlock.FormulaBlock -> {
                                                    BlockFormulaCard(
                                                        formula = block,
                                                        onExpand = { onExpandFormula(block) }
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                                is ContentBlock.MermaidBlock -> {
                                                    MermaidDiagramCard(
                                                        block = block,
                                                        onExpand = { onExpandMermaid(block) }
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                                is ContentBlock.SvgBlock -> {
                                                    SvgGraphicCard(
                                                        block = block,
                                                        onExpand = { onExpandSvg(block) }
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                                is ContentBlock.TableBlock -> {
                                                    RenderMarkdownTable(table = block)
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                            }
                                        }

                                        // Generating animation on paper sheet
                                        if (isGenerating) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 16.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Groq AI is composing document...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontStyle = FontStyle.Italic,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Page Footer
                                        Spacer(modifier = Modifier.height(32.dp))
                                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Page 1",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "GroqChat Document Editor",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        DocumentExportDialog(
            title = documentTitle,
            content = fullDocumentContent,
            onDismiss = { showExportDialog = false }
        )
    }
}

/**
 * Top Header: Word / Google Docs style window banner with File/Edit/Insert menus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordProcessorTopHeader(
    documentTitle: String,
    isSaved: Boolean,
    onOpenNavigation: () -> Unit,
    onOpenSettings: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    showMenuDropdown: String?,
    onMenuClick: (String) -> Unit,
    onDismissMenu: () -> Unit,
    onInsertEquation: () -> Unit,
    onInsertDiagram: () -> Unit,
    onInsertCode: () -> Unit,
    onInsertSvg: () -> Unit,
    onClearDocument: () -> Unit,
    onViewModeChange: (WordProcessorViewMode) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Document Title & Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Word Processor Document Icon (Blue Word/Docs badge)
                IconButton(onClick = onOpenNavigation, modifier = Modifier.size(36.dp).testTag("document_menu_button")) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Document Navigation",
                        tint = Color(0xFF2563EB), // Iconic Word / Docs Royal Blue
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Editable Document Title + Status Pill
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (documentTitle.endsWith(".docx")) documentTitle else "$documentTitle.docx",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = "Star",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Save Status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.CloudDone else Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = if (isSaved) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSaved) "Saved to device" else "Saving changes...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                // Top Right Action Buttons: Share, Export, Settings
                IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Export Document", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp).testTag("wp_settings_button")) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                }
            }

            // Word Processor Traditional Menu Bar: File • Edit • View • Insert • Format • Tools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val menuItems = listOf("File", "Edit", "View", "Insert", "Format", "Tools")

                menuItems.forEach { item ->
                    Box {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (showMenuDropdown == item) FontWeight.Bold else FontWeight.Normal,
                            color = if (showMenuDropdown == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onMenuClick(item) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        // Dropdowns for standard word processor menus
                        DropdownMenu(
                            expanded = showMenuDropdown == item,
                            onDismissRequest = onDismissMenu
                        ) {
                            when (item) {
                                "File" -> {
                                    DropdownMenuItem(
                                        text = { Text("Export as .docx / Markdown") },
                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                        onClick = { onDismissMenu(); onExport() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Print / Share") },
                                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                        onClick = { onDismissMenu(); onShare() }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Clear Document Content") },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                        onClick = { onDismissMenu(); onClearDocument() }
                                    )
                                }
                                "Edit" -> {
                                    DropdownMenuItem(
                                        text = { Text("Select All & Copy") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                        onClick = { onDismissMenu() }
                                    )
                                }
                                "View" -> {
                                    DropdownMenuItem(
                                        text = { Text("Print Layout (Paper Sheet)") },
                                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                                        onClick = { onDismissMenu(); onViewModeChange(WordProcessorViewMode.PRINT_LAYOUT) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Web Layout (Continuous Flow)") },
                                        leadingIcon = { Icon(Icons.Default.Web, contentDescription = null) },
                                        onClick = { onDismissMenu(); onViewModeChange(WordProcessorViewMode.WEB_LAYOUT) }
                                    )
                                }
                                "Insert" -> {
                                    DropdownMenuItem(
                                        text = { Text("Equation (\$x^2\$ KaTeX)") },
                                        leadingIcon = { Icon(Icons.Default.Functions, contentDescription = null) },
                                        onClick = { onDismissMenu(); onInsertEquation() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Diagram (Mermaid)") },
                                        leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                                        onClick = { onDismissMenu(); onInsertDiagram() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Vector Graphic (SVG)") },
                                        leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null) },
                                        onClick = { onDismissMenu(); onInsertSvg() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Code Block") },
                                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                                        onClick = { onDismissMenu(); onInsertCode() }
                                    )
                                }
                                "Format" -> {
                                    DropdownMenuItem(
                                        text = { Text("Heading 1 (H1)") },
                                        onClick = { onDismissMenu() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Heading 2 (H2)") },
                                        onClick = { onDismissMenu() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Bullet List") },
                                        leadingIcon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) },
                                        onClick = { onDismissMenu() }
                                    )
                                }
                                "Tools" -> {
                                    DropdownMenuItem(
                                        text = { Text("Word Count") },
                                        leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                                        onClick = { onDismissMenu() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Spelling & Grammar Check") },
                                        leadingIcon = { Icon(Icons.Default.Spellcheck, contentDescription = null) },
                                        onClick = { onDismissMenu() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

/**
 * Word Processor Formatting Ribbon (Toolbar)
 * Office Ribbon / Docs Toolbar with font styles, sizing, B/I/U/S, alignment, and inserts.
 */
@Composable
fun WordProcessorRibbon(
    selectedStyle: String,
    onStyleChange: (String) -> Unit,
    fontFamily: String,
    onFontFamilyChange: (String) -> Unit,
    fontSizePt: Int,
    onFontSizeChange: (Int) -> Unit,
    isBold: Boolean,
    onBoldToggle: () -> Unit,
    isItalic: Boolean,
    onItalicToggle: () -> Unit,
    isUnderline: Boolean,
    onUnderlineToggle: () -> Unit,
    isStrikethrough: Boolean,
    onStrikethroughToggle: () -> Unit,
    textAlign: TextAlign,
    onTextAlignChange: (TextAlign) -> Unit,
    onInsertEquation: () -> Unit,
    onInsertDiagram: () -> Unit,
    onInsertSvg: () -> Unit,
    onInsertCode: () -> Unit,
    onCopilotPrompt: () -> Unit
) {
    val hScroll = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo / Redo
            IconButton(onClick = {}, modifier = Modifier.size(30.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = {}, modifier = Modifier.size(30.dp)) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(16.dp))
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

            // Style dropdown pill (Normal text / Heading 1)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(selectedStyle, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }

            // Font size stepper (- 12 +)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = { onFontSizeChange((fontSizePt - 1).coerceAtLeast(8)) }, modifier = Modifier.size(24.dp)) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text("$fontSizePt", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                IconButton(onClick = { onFontSizeChange((fontSizePt + 1).coerceAtMost(36)) }, modifier = Modifier.size(24.dp)) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

            // Formatting B, I, U, S buttons
            RibbonFormatButton(label = "B", isSelected = isBold, onClick = onBoldToggle)
            RibbonFormatButton(label = "I", isItalic = true, isSelected = isItalic, onClick = onItalicToggle)
            RibbonFormatButton(label = "U", isUnderline = true, isSelected = isUnderline, onClick = onUnderlineToggle)
            RibbonFormatButton(label = "S", isStrikethrough = true, isSelected = isStrikethrough, onClick = onStrikethroughToggle)

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

            // Text Alignment buttons
            IconButton(onClick = { onTextAlignChange(TextAlign.Start) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.FormatAlignLeft, contentDescription = "Align Left", tint = if (textAlign == TextAlign.Start) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { onTextAlignChange(TextAlign.Center) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.FormatAlignCenter, contentDescription = "Align Center", tint = if (textAlign == TextAlign.Center) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
            }

            VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

            // Insert Shortcuts in Ribbon
            FilterChip(
                selected = false,
                onClick = onInsertEquation,
                label = { Text("∑ Equation", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp).padding(end = 4.dp)
            )
            FilterChip(
                selected = false,
                onClick = onInsertDiagram,
                label = { Text("📊 Diagram", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp).padding(end = 4.dp)
            )
            FilterChip(
                selected = false,
                onClick = onInsertCode,
                label = { Text("💻 Code", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp).padding(end = 4.dp)
            )
            FilterChip(
                selected = false,
                onClick = onInsertSvg,
                label = { Text("🎨 SVG", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp).padding(end = 4.dp)
            )

            // Groq AI Co-Writer Copilot button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onCopilotPrompt() }
                    .padding(start = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copilot", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun RibbonFormatButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isItalic: Boolean = false,
    isUnderline: Boolean = false,
    isStrikethrough: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (label == "B") FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = when {
                    isUnderline -> TextDecoration.Underline
                    isStrikethrough -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                },
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Famous Word Processor Horizontal Ruler
 * Displays inch/cm tick marks across the document width.
 */
@Composable
fun WordProcessorRuler(zoomLevel: Float) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth().height(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 32.dp.toPx() * zoomLevel
            var x = 0f
            var unit = 0
            while (x < size.width) {
                // Major tick mark
                drawLine(
                    color = Color.Gray.copy(alpha = 0.4f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 1f
                )
                // Half tick
                val halfX = x + (step / 2)
                if (halfX < size.width) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.25f),
                        start = androidx.compose.ui.geometry.Offset(halfX, size.height * 0.4f),
                        end = androidx.compose.ui.geometry.Offset(halfX, size.height),
                        strokeWidth = 0.8f
                    )
                }
                x += step
                unit++
            }
        }
    }
}

/**
 * Word Processor Status Bar (Bottom of Window)
 * Displays page count, word count, spellcheck status, and zoom slider.
 */
@Composable
fun WordProcessorStatusBar(
    wordCount: Int,
    charCount: Int,
    viewMode: WordProcessorViewMode,
    onViewModeChange: (WordProcessorViewMode) -> Unit,
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Status: Page 1 of 1 • Word & Character Count • Spellcheck
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Page 1 of 1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$wordCount words",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Spelling check passed",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(12.dp)
                )
            }

            // Right Status: View mode toggles + Zoom slider (- 100% +)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Print layout icon
                IconButton(
                    onClick = { onViewModeChange(WordProcessorViewMode.PRINT_LAYOUT) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Print Layout",
                        tint = if (viewMode == WordProcessorViewMode.PRINT_LAYOUT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Web layout icon
                IconButton(
                    onClick = { onViewModeChange(WordProcessorViewMode.WEB_LAYOUT) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Web,
                        contentDescription = "Web Layout",
                        tint = if (viewMode == WordProcessorViewMode.WEB_LAYOUT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Zoom Level Controls
                IconButton(onClick = { onZoomChange(zoomLevel - 0.1f) }, modifier = Modifier.size(20.dp)) {
                    Text("-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${(zoomLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                IconButton(onClick = { onZoomChange(zoomLevel + 0.1f) }, modifier = Modifier.size(20.dp)) {
                    Text("+", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Word Copilot Dock
 * Sleek bottom prompt bar inspired by Microsoft Copilot in Word / Google Gemini in Docs.
 */
@Composable
fun WordProcessorCopilotDock(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    isGenerating: Boolean,
    isListeningSpeech: Boolean,
    onToggleSpeech: () -> Unit,
    selectedModel: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copilot Spark Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Copilot",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Prompt Input Box
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    placeholder = { Text("Ask Groq to draft, write formulas \$x^2\$, or generate diagrams...") },
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copilot_input_field"),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Voice Speech Mic Button
                IconButton(
                    onClick = onToggleSpeech,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (isListeningSpeech) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isListeningSpeech) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice dictation",
                        tint = if (isListeningSpeech) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Generate / Co-write Button
                IconButton(
                    onClick = { onSendMessage(null) },
                    enabled = inputText.isNotBlank() && !isGenerating,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (inputText.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            CircleShape
                        )
                        .testTag("copilot_send_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Generate",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Navigation Pane / Document Outline Drawer
 * Mimics Word's Navigation Pane and Google Docs Document Outline.
 */
@Composable
fun DocumentNavigationPane(
    documentTitle: String,
    wordCount: Int,
    charCount: Int,
    headings: List<Pair<Int, String>>,
    sessions: List<ChatSession>,
    currentSessionId: String,
    onSelectSession: (String) -> Unit,
    onNewDocument: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Pane Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Navigation Pane", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onCloseDrawer, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // New Document Button
        Button(
            onClick = onNewDocument,
            modifier = Modifier.fillMaxWidth().testTag("new_document_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("New Document")
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Document Statistics Box
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("Document Summary", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("$wordCount words • $charCount characters", style = MaterialTheme.typography.bodySmall)
                Text("${(wordCount / 200).coerceAtLeast(1)} min estimated reading time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab: Document Headings (Table of Contents Outline)
        Text(
            text = "DOCUMENT OUTLINE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (headings.isEmpty()) {
            Text(
                "Headings added to the document will show here for quick jumping.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(0.4f)) {
                items(headings) { (level, title) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCloseDrawer() }
                            .padding(start = (level * 8).dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (level == 1) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        // Recent Documents List
        Text(
            text = "ALL DOCUMENTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(0.6f)) {
            items(sessions) { doc ->
                val isSelected = doc.id == currentSessionId
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectSession(doc.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (doc.title.endsWith(".docx")) doc.title else "${doc.title}.docx",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDeleteDocument(doc.id) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty Document Sheet with template cards.
 */
@Composable
fun EmptyDocumentSheet(
    onSelectTemplate: (String) -> Unit,
    sheetBackground: Color,
    zoomLevel: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .graphicsLayer(scaleX = zoomLevel, scaleY = zoomLevel)
                .shadow(4.dp, RoundedCornerShape(2.dp))
                .border(0.5.dp, Color(0x22000000), RoundedCornerShape(2.dp))
                .background(sheetBackground, RoundedCornerShape(2.dp))
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Untitled Document",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Draft, typeset formulas, generate Mermaid diagrams, and write code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Start with a Document Template:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            val templates = listOf(
                "Scientific Research Paper with Formulas" to "Write a research paper on quantum mechanics including equations like \$E = mc^2\$ and \$\\int_{-\\infty}^{\\infty} e^{-x^2} dx = \\sqrt{\\pi}\$",
                "Software Architecture Specification" to "Draft a technical architecture specification with a Mermaid flowchart diagram and Kotlin implementation code",
                "Mathematics Problem Set & Proofs" to "Create a calculus problem set with step-by-step typeset solutions for derivatives and integrals",
                "Product Requirements Document (PRD)" to "Generate a complete PRD with feature breakdown tables, user journey diagram, and system metrics"
            )

            templates.forEach { (title, prompt) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectTemplate(prompt) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Article, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(prompt.take(65) + "...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Export Document Dialog (Export as Word / Markdown / HTML / Plain Text)
 */
@Composable
fun DocumentExportDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Document") },
        text = {
            Column {
                Text("Select an export format for $title:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "$title.md")
                            putExtra(Intent.EXTRA_TEXT, content)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Markdown"))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share / Save as Markdown (.md)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(content))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (copied) "Copied to Clipboard!" else "Copy Full Document Text")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
