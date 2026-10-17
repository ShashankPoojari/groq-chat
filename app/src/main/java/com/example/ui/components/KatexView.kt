package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ContentBlock
import com.example.syntax.CodeHighlighter

/**
 * High-fidelity KaTeX WebView component that renders formulas with KaTeX library.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KatexWebView(
    latex: String,
    isDisplayMode: Boolean = true,
    fontSizeEm: Float = 1.2f,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) "#F1F5F9" else "#0F172A"
    val bgColor = "transparent"

    val escapedLatex = latex
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
            <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
            <style>
                body {
                    margin: 0;
                    padding: 8px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    background-color: $bgColor;
                    color: $textColor;
                    font-size: ${fontSizeEm}em;
                    overflow: auto;
                }
                #math-target {
                    text-align: center;
                    min-height: 24px;
                }
            </style>
        </head>
        <body>
            <div id="math-target">...</div>
            <script>
                document.addEventListener("DOMContentLoaded", function() {
                    try {
                        katex.render('$escapedLatex', document.getElementById('math-target'), {
                            displayMode: $isDisplayMode,
                            throwOnError: false
                        });
                    } catch(e) {
                        document.getElementById('math-target').innerText = '$escapedLatex';
                    }
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
 * Full-screen viewer for large formulas.
 * Supports pinch-to-zoom, pan, LaTeX source toggle, and copy/share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenFormulaDialog(
    formula: ContentBlock.FormulaBlock,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Typeset, 1 = LaTeX Source
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var copiedNotification by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("full_screen_formula_dialog"),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Formula Viewer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Mathematical Expression",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_formula_viewer")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        // Zoom in
                        IconButton(onClick = { scale = (scale + 0.3f).coerceAtMost(5.0f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                        }
                        // Zoom out
                        IconButton(onClick = { scale = (scale - 0.3f).coerceAtLeast(0.5f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                        }
                        // Reset zoom
                        IconButton(onClick = {
                            scale = 1.0f
                            offsetX = 0f
                            offsetY = 0f
                        }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom")
                        }
                        // Copy LaTeX
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(formula.formula))
                            copiedNotification = true
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy LaTeX")
                        }
                        // Share
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "LaTeX Formula")
                                putExtra(Intent.EXTRA_TEXT, "$$" + formula.formula + "$$")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Formula"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Typeset View") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("LaTeX Source") }
                            )
                        }

                        AnimatedVisibility(visible = copiedNotification) {
                            Text(
                                "Formula copied to clipboard!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (selectedTab == 0) {
                    // Interactive Typeset Canvas with Pinch to Zoom and Pan
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Primary high-res typeset output
                            val formatted = remember(formula.formula) {
                                LatexFormatter.formatMathAnnotated(formula.formula, isDisplay = true)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 32.dp, vertical = 28.dp)
                            ) {
                                Text(
                                    text = formatted,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        // Zoom level indicator overlay
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // LaTeX Source Tab with Syntax Highlighting
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            val highlightedLatex = remember(formula.formula) {
                                CodeHighlighter.highlight(formula.formula, "latex")
                            }
                            Text(
                                text = highlightedLatex,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
