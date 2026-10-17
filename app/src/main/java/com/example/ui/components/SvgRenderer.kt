package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ContentBlock
import com.example.syntax.CodeHighlighter

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SvgWebView(
    svgCode: String,
    backgroundColorHex: String = "transparent",
    modifier: Modifier = Modifier
) {
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    margin: 0;
                    padding: 8px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    background-color: $backgroundColorHex;
                    overflow: auto;
                }
                svg {
                    max-width: 100% !important;
                    height: auto !important;
                }
            </style>
        </head>
        <body>
            $svgCode
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
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

/**
 * Card displaying an SVG vector graphic in the chat.
 */
@Composable
fun SvgGraphicCard(
    block: ContentBlock.SvgBlock,
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
            .testTag("svg_graphic_card")
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
                        imageVector = Icons.Default.Brush,
                        contentDescription = "SVG",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SVG Vector Graphic",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { showSource = !showSource },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (showSource) "Graphic" else "Source",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(block.svgCode))
                            copied = true
                        },
                        modifier = Modifier.size(28.dp).testTag("copy_svg_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy SVG",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(28.dp).testTag("expand_svg_button")
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

            // Graphic or Raw XML
            if (!showSource) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    SvgWebView(
                        svgCode = block.svgCode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                val highlighted = remember(block.svgCode) {
                    CodeHighlighter.highlight(block.svgCode, "xml")
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
 * Full-screen viewer for SVG graphics with pinch to zoom and pan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenSvgDialog(
    block: ContentBlock.SvgBlock,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var bgThemeIndex by remember { mutableIntStateOf(0) } // 0 = Slate, 1 = Black, 2 = White
    var showSource by remember { mutableStateOf(false) }

    val bgColors = listOf("#0F172A", "#000000", "#FFFFFF")
    val composeBgColors = listOf(Color(0xFF0F172A), Color(0xFF000000), Color(0xFFFFFFFF))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("full_screen_svg_dialog"),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "SVG Vector Viewer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scalable Vector Graphic",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_svg_viewer")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        // Background switcher
                        IconButton(onClick = { bgThemeIndex = (bgThemeIndex + 1) % bgColors.size }) {
                            Icon(Icons.Default.Palette, contentDescription = "Change background")
                        }

                        // Toggle code
                        IconButton(onClick = { showSource = !showSource }) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = "Source",
                                tint = if (showSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Copy SVG
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(block.svgCode))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy SVG")
                        }

                        // Share
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/svg+xml"
                                putExtra(Intent.EXTRA_SUBJECT, "Vector Graphic.svg")
                                putExtra(Intent.EXTRA_TEXT, block.svgCode)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share SVG"))
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
                    .background(composeBgColors[bgThemeIndex])
            ) {
                if (!showSource) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.5f, 8.0f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .padding(16.dp)
                        ) {
                            SvgWebView(
                                svgCode = block.svgCode,
                                backgroundColorHex = bgColors[bgThemeIndex],
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Zoom indicator and reset
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    scale = 1.0f
                                    offsetX = 0f
                                    offsetY = 0f
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = "${(scale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    val highlighted = remember(block.svgCode) {
                        CodeHighlighter.highlight(block.svgCode, "xml")
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
