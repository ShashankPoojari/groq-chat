package com.example.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val model: String? = null,
    val tokensPerSecond: Double? = null,
    val isStreaming: Boolean = false,
    val error: String? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val isPinned: Boolean = false
)

sealed interface ContentBlock {
    data class TextBlock(
        val text: String,
        val inlineFormulas: List<InlineFormulaSpan> = emptyList()
    ) : ContentBlock

    data class CodeBlock(
        val language: String,
        val code: String,
        val lineCount: Int = code.lines().size
    ) : ContentBlock

    data class FormulaBlock(
        val formula: String,
        val rawLatex: String = formula,
        val isDisplayMode: Boolean = true
    ) : ContentBlock

    data class MermaidBlock(
        val diagramCode: String,
        val diagramType: String = detectMermaidType(diagramCode)
    ) : ContentBlock

    data class SvgBlock(
        val svgCode: String,
        val title: String = "Vector Graphic"
    ) : ContentBlock

    data class TableBlock(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : ContentBlock
}

data class InlineFormulaSpan(
    val id: String = UUID.randomUUID().toString(),
    val formula: String,
    val startIndex: Int,
    val endIndex: Int
)

data class AppSettings(
    val groqApiKey: String = "",
    val selectedModel: String = "llama-3.3-70b-versatile",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "You are GroqChat, a brilliant and helpful AI assistant. Always typeset mathematical formulas using LaTeX (\$x^2\$ for inline, \$\$ ... \$\$ for block). Use appropriate programming languages for code blocks. Generate Mermaid diagrams for workflows and SVG for vector graphics when helpful.",
    val formulaRenderMode: String = "KaTeX", // "KaTeX" or "Typeset"
    val showLineNumbersByDefault: Boolean = true,
    val wordWrapByDefault: Boolean = false,
    val codeFontSizeSp: Float = 13f,
    val hapticFeedback: Boolean = true,
    val voiceTtsEnabled: Boolean = true
)

fun detectMermaidType(code: String): String {
    val trimmed = code.trim().lowercase()
    return when {
        trimmed.startsWith("graph") || trimmed.startsWith("flowchart") -> "Flowchart"
        trimmed.startsWith("sequencediagram") -> "Sequence Diagram"
        trimmed.startsWith("classdiagram") -> "Class Diagram"
        trimmed.startsWith("statediagram") -> "State Diagram"
        trimmed.startsWith("erdiagram") -> "Entity Relationship"
        trimmed.startsWith("gantt") -> "Gantt Chart"
        trimmed.startsWith("pie") -> "Pie Chart"
        trimmed.startsWith("gitgraph") -> "Git Graph"
        trimmed.startsWith("mindmap") -> "Mindmap"
        trimmed.startsWith("quadrantchart") -> "Quadrant Chart"
        trimmed.startsWith("timeline") -> "Timeline"
        else -> "Mermaid Diagram"
    }
}
