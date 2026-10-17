package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.model.ContentBlock
import com.example.model.MessageRole
import com.example.parser.MarkdownParser
import com.example.syntax.CodeHighlighter

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onExpandCode: (ContentBlock.CodeBlock) -> Unit,
    onExpandFormula: (ContentBlock.FormulaBlock) -> Unit,
    onExpandMermaid: (ContentBlock.MermaidBlock) -> Unit,
    onExpandSvg: (ContentBlock.SvgBlock) -> Unit,
    onSpeak: (String) -> Unit,
    onRegenerate: () -> Unit,
    isSpeakingThis: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val blocks = remember(message.content) {
        MarkdownParser.parse(message.content)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Assistant Avatar
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Groq",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Message Card
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) else null,
                modifier = Modifier.testTag("chat_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (blocks.isEmpty() && message.content.isNotBlank()) {
                        Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        blocks.forEach { block ->
                            when (block) {
                                is ContentBlock.TextBlock -> {
                                    RenderTextBlockWithInlineFormulas(
                                        block = block,
                                        isUser = isUser,
                                        onExpandFormula = { formulaLatex ->
                                            onExpandFormula(ContentBlock.FormulaBlock(formula = formulaLatex, isDisplayMode = false))
                                        }
                                    )
                                }
                                is ContentBlock.CodeBlock -> {
                                    RenderCodeBlockCard(
                                        codeBlock = block,
                                        onExpand = { onExpandCode(block) }
                                    )
                                }
                                is ContentBlock.FormulaBlock -> {
                                    BlockFormulaCard(
                                        formula = block,
                                        onExpand = { onExpandFormula(block) }
                                    )
                                }
                                is ContentBlock.MermaidBlock -> {
                                    MermaidDiagramCard(
                                        block = block,
                                        onExpand = { onExpandMermaid(block) }
                                    )
                                }
                                is ContentBlock.SvgBlock -> {
                                    SvgGraphicCard(
                                        block = block,
                                        onExpand = { onExpandSvg(block) }
                                    )
                                }
                                is ContentBlock.TableBlock -> {
                                    RenderMarkdownTable(table = block)
                                }
                            }
                        }
                    }

                    if (message.isStreaming) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Generating response...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Message Actions & Stats Bar (for Assistant)
            if (!isUser && message.content.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                ) {
                    message.tokensPerSecond?.let { tps ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "${tps.toInt()} t/s",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            copied = true
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSpeak(message.content) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeakingThis) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Read aloud",
                            tint = if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // User Avatar
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Renders sentences with embedded typeset inline formulas ($x^2$).
 * Sentences flow naturally and inline math chips are typeset rather than approximated!
 */
@Composable
fun RenderTextBlockWithInlineFormulas(
    block: ContentBlock.TextBlock,
    isUser: Boolean,
    onExpandFormula: (String) -> Unit
) {
    if (block.inlineFormulas.isEmpty()) {
        Text(
            text = formatSimpleMarkdown(block.text),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    } else {
        // Flowing paragraph with inline typeset math chips
        val spans = block.inlineFormulas
        var lastIndex = 0

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            // Split by lines or format inline chunks
            val segments = mutableListOf<@Composable () -> Unit>()

            for (span in spans) {
                if (span.startIndex > lastIndex) {
                    val textBefore = block.text.substring(lastIndex, span.startIndex)
                    if (textBefore.isNotEmpty()) {
                        segments.add {
                            Text(
                                text = formatSimpleMarkdown(textBefore),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                segments.add {
                    InlineFormulaChip(
                        formula = span.formula,
                        onExpandFormula = onExpandFormula
                    )
                }

                lastIndex = span.endIndex
            }

            if (lastIndex < block.text.length) {
                val textAfter = block.text.substring(lastIndex)
                if (textAfter.isNotEmpty()) {
                    segments.add {
                        Text(
                            text = formatSimpleMarkdown(textAfter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Render flow or wrapped elements
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center
            ) {
                segments.forEach { segment ->
                    segment()
                }
            }
        }
    }
}

/**
 * Compact Code Block Card in Chat bubble with quick Copy and Full-Screen expand button.
 */
@Composable
fun RenderCodeBlockCard(
    codeBlock: ContentBlock.CodeBlock,
    onExpand: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val highlighted = remember(codeBlock.code, codeBlock.language) {
        CodeHighlighter.highlight(codeBlock.code, codeBlock.language)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF181A1F)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF2C313C)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("code_block_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF21252B))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = codeBlock.language.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF61AFEF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${codeBlock.lineCount} lines",
                        fontSize = 10.sp,
                        color = Color(0xFF5C6370)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(codeBlock.code))
                            copied = true
                        },
                        modifier = Modifier.size(26.dp).testTag("copy_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) MaterialTheme.colorScheme.primary else Color(0xFFABB2BF),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Full-screen expand
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(26.dp).testTag("expand_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand to Full-screen",
                            tint = Color(0xFFABB2BF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Code Preview
            val hScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
                    .clickable { onExpand() }
                    .padding(12.dp)
            ) {
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = CodeHighlighter.DefaultTextColor
                )
            }
        }
    }
}

/**
 * Renders Markdown tables nicely with scrollable grid.
 */
@Composable
fun RenderMarkdownTable(table: ContentBlock.TableBlock) {
    val hScroll = rememberScrollState()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
                .padding(8.dp)
        ) {
            // Headers
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))) {
                table.headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp).widthIn(min = 80.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Rows
            table.rows.forEachIndexed { idx, row ->
                Row(
                    modifier = Modifier.background(
                        if (idx % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp).widthIn(min = 80.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatSimpleMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        // Bold: **text**
        val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
        for (match in boldRegex.findAll(text)) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        }
        // Italic: *text*
        val italicRegex = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")
        for (match in italicRegex.findAll(text)) {
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
        }
        // Inline code: `code`
        val codeRegex = Regex("`([^`]+)`")
        for (match in codeRegex.findAll(text)) {
            addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x33888888),
                    color = Color(0xFFF97316)
                ),
                match.range.first,
                match.range.last + 1
            )
        }
    }
}
