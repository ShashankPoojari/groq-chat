package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ContentBlock
import com.example.syntax.CodeHighlighter

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(
    code: String,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val themeName = if (isDarkTheme) "dark" else "default"
    val bgColor = if (isDarkTheme) "#181A1F" else "#FFFFFF"

    val sanitizedCode = code
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.0/dist/mermaid.min.js"></script>
            <style>
                body {
                    margin: 0;
                    padding: 12px;
                    background-color: $bgColor;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    overflow: auto;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                }
                .mermaid {
                    width: 100%;
                    display: flex;
                    justify-content: center;
                }
                svg {
                    max-width: 100% !important;
                    height: auto !important;
                }
            </style>
        </head>
        <body>
            <div class="mermaid">
$sanitizedCode
            </div>
            <script>
                mermaid.initialize({
                    startOnLoad: true,
                    theme: '$themeName',
                    securityLevel: 'loose',
                    flowchart: { useMaxWidth: true, htmlLabels: true }
                });
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(0)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

/**
 * Chat card displaying a Mermaid diagram with source toggle and full-screen button.
 */
@Composable
fun MermaidDiagramCard(
    block: ContentBlock.MermaidBlock,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var showSource by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .animateContentSize()
            .testTag("mermaid_diagram_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = "Diagram",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = block.diagramType,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Source toggle
                    TextButton(
                        onClick = { showSource = !showSource },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (showSource) "Diagram" else "Source",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Copy source
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(block.diagramCode))
                            copied = true
                        },
                        modifier = Modifier.size(28.dp).testTag("copy_mermaid_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Full-screen expand
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(28.dp).testTag("expand_mermaid_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full-screen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Diagram visual or raw source
            if (!showSource) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    MermaidWebView(
                        code = block.diagramCode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                val highlighted = remember(block.diagramCode) {
                    CodeHighlighter.highlight(block.diagramCode, "mermaid")
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181A1F))
                        .padding(12.dp)
                ) {
                    Text(
                        text = highlighted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = CodeHighlighter.DefaultTextColor
                    )
                }
            }
        }
    }
}

/**
 * Full-screen viewer for Mermaid diagrams with pinch-to-zoom and theme toggle.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenMermaidDialog(
    block: ContentBlock.MermaidBlock,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isDarkTheme by remember { mutableStateOf(true) }
    var showSource by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("full_screen_mermaid_dialog"),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = block.diagramType,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mermaid Diagram Viewer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_mermaid_viewer")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        // Theme toggle (Dark / Light diagram preview)
                        IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }

                        // Toggle code view
                        IconButton(onClick = { showSource = !showSource }) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Toggle Source",
                                tint = if (showSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Copy Mermaid code
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(block.diagramCode))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy code")
                        }

                        // Share
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, block.diagramType)
                                putExtra(Intent.EXTRA_TEXT, "```mermaid\n${block.diagramCode}\n```")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Diagram"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            ) {
                if (!showSource) {
                    MermaidWebView(
                        code = block.diagramCode,
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val highlighted = remember(block.diagramCode) {
                        CodeHighlighter.highlight(block.diagramCode, "mermaid")
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF181A1F))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = highlighted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = CodeHighlighter.DefaultTextColor
                        )
                    }
                }
            }
        }
    }
}
