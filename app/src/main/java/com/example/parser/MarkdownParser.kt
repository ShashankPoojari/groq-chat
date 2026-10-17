package com.example.parser

import com.example.model.ContentBlock
import com.example.model.InlineFormulaSpan

object MarkdownParser {

    private val CODE_OR_BLOCK_REGEX = Regex("```([a-zA-Z0-9_+#.-]*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    private val BLOCK_FORMULA_REGEX = Regex("(\\$\\$([\\s\\S]*?)\\$\\$|\\\\\\[([\\s\\S]*?)\\\\\\])", RegexOption.MULTILINE)
    private val STANDALONE_SVG_REGEX = Regex("(?s)<svg\\b[^>]*>(.*?)</svg>", RegexOption.IGNORE_CASE)
    // Matches $...$ without spaces immediately inside the delimiters, and avoiding currency like $100
    private val INLINE_FORMULA_REGEX = Regex("(?<!\\\\)\\$(?!\\s)([^$\\n]+?)(?<!\\s)\\$(?!\\d)")
    private val ALT_INLINE_FORMULA_REGEX = Regex("\\\\\\((.*?)\\\\\\)")

    private enum class SegmentType { CODE, FORMULA, RAW_SVG }

    private data class MatchedSegment(
        val start: Int,
        val end: Int,
        val type: SegmentType,
        val payload1: String,
        val payload2: String
    )

    fun parse(content: String): List<ContentBlock> {
        if (content.isBlank()) return emptyList()

        val blocks = mutableListOf<ContentBlock>()
        var cursor = 0

        val matches = mutableListOf<MatchedSegment>()

        // Find code blocks
        for (match in CODE_OR_BLOCK_REGEX.findAll(content)) {
            val lang = match.groupValues[1].trim()
            val code = match.groupValues[2]
            matches.add(MatchedSegment(match.range.first, match.range.last + 1, SegmentType.CODE, lang, code))
        }

        // Find block formulas ($$ ... $$)
        for (match in BLOCK_FORMULA_REGEX.findAll(content)) {
            val formula = (match.groupValues[2].takeIf { it.isNotEmpty() } ?: match.groupValues[3]).trim()
            matches.add(MatchedSegment(match.range.first, match.range.last + 1, SegmentType.FORMULA, formula, ""))
        }

        // Find standalone SVG blocks outside code blocks
        for (match in STANDALONE_SVG_REGEX.findAll(content)) {
            val svg = match.value.trim()
            matches.add(MatchedSegment(match.range.first, match.range.last + 1, SegmentType.RAW_SVG, svg, ""))
        }

        // Sort matches by start index and filter out overlapping ranges
        val sortedMatches = matches.sortedBy { it.start }
        val nonOverlapping = mutableListOf<MatchedSegment>()
        var lastEnd = -1
        for (m in sortedMatches) {
            if (m.start >= lastEnd) {
                nonOverlapping.add(m)
                lastEnd = m.end
            }
        }

        for (segment in nonOverlapping) {
            if (segment.start > cursor) {
                val textChunk = content.substring(cursor, segment.start)
                if (textChunk.isNotBlank()) {
                    blocks.addAll(parseTextAndTables(textChunk))
                }
            }

            when (segment.type) {
                SegmentType.CODE -> {
                    val lang = segment.payload1.lowercase()
                    val code = segment.payload2.trimEnd()

                    when {
                        lang == "mermaid" -> {
                            blocks.add(ContentBlock.MermaidBlock(diagramCode = code))
                        }
                        lang == "svg" -> {
                            blocks.add(ContentBlock.SvgBlock(svgCode = code))
                        }
                        (lang == "xml" || lang == "html") && code.trim().startsWith("<svg", ignoreCase = true) -> {
                            blocks.add(ContentBlock.SvgBlock(svgCode = code))
                        }
                        lang == "latex" || lang == "tex" || lang == "math" -> {
                            blocks.add(ContentBlock.FormulaBlock(formula = code, isDisplayMode = true))
                        }
                        else -> {
                            blocks.add(ContentBlock.CodeBlock(language = segment.payload1.ifBlank { "plaintext" }, code = code))
                        }
                    }
                }
                SegmentType.FORMULA -> {
                    blocks.add(ContentBlock.FormulaBlock(formula = segment.payload1, isDisplayMode = true))
                }
                SegmentType.RAW_SVG -> {
                    blocks.add(ContentBlock.SvgBlock(svgCode = segment.payload1))
                }
            }

            cursor = segment.end
        }

        if (cursor < content.length) {
            val tail = content.substring(cursor)
            if (tail.isNotBlank()) {
                blocks.addAll(parseTextAndTables(tail))
            }
        }

        return blocks
    }

    private fun parseTextAndTables(chunk: String): List<ContentBlock> {
        val result = mutableListOf<ContentBlock>()
        val lines = chunk.lines()
        var i = 0

        val textBuffer = StringBuilder()

        fun flushText() {
            if (textBuffer.isNotBlank()) {
                val fullText = textBuffer.toString().trim()
                if (fullText.isNotEmpty()) {
                    val spans = extractInlineFormulas(fullText)
                    result.add(ContentBlock.TextBlock(text = fullText, inlineFormulas = spans))
                }
                textBuffer.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            // Check for Markdown table line: | Header | Header |
            if (line.trim().startsWith("|") && line.trim().endsWith("|") && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                val isTableSeparator = nextLine.startsWith("|") && nextLine.contains("---")
                if (isTableSeparator) {
                    flushText()
                    val headers = parseTableRow(line)
                    val rows = mutableListOf<List<String>>()
                    i += 2 // skip header and separator
                    while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                        rows.add(parseTableRow(lines[i]))
                        i++
                    }
                    result.add(ContentBlock.TableBlock(headers, rows))
                    continue
                }
            }

            textBuffer.append(line).append("\n")
            i++
        }

        flushText()
        return result
    }

    private fun parseTableRow(row: String): List<String> {
        return row.trim().removeSurrounding("|").split("|").map { it.trim() }
    }

    fun extractInlineFormulas(text: String): List<InlineFormulaSpan> {
        val spans = mutableListOf<InlineFormulaSpan>()

        // 1. Check $...$ matches
        for (match in INLINE_FORMULA_REGEX.findAll(text)) {
            val formula = match.groupValues[1].trim()
            if (formula.isNotEmpty() && isLikelyMath(formula)) {
                spans.add(InlineFormulaSpan(formula = formula, startIndex = match.range.first, endIndex = match.range.last + 1))
            }
        }

        // 2. Check \( ... \) matches
        for (match in ALT_INLINE_FORMULA_REGEX.findAll(text)) {
            val formula = match.groupValues[1].trim()
            if (formula.isNotEmpty()) {
                spans.add(InlineFormulaSpan(formula = formula, startIndex = match.range.first, endIndex = match.range.last + 1))
            }
        }

        return spans.sortedBy { it.startIndex }
    }

    private fun isLikelyMath(text: String): Boolean {
        // Exclude simple currency or isolated words without math symbols
        if (text.all { it.isDigit() || it == '.' || it == ',' }) return false
        val mathChars = listOf(
            "^", "_", "\\", "+", "-", "=", "*", "/", "<", ">",
            "\\alpha", "\\beta", "\\gamma", "\\theta", "\\pi", "\\lambda", "\\sigma", "\\omega",
            "\\sqrt", "\\frac", "\\sum", "\\int", "\\prod", "\\lim", "\\partial", "\\infty",
            "\\cdot", "\\pm", "\\approx", "\\neq", "\\le", "\\ge", "\\in", "\\subset",
            "{", "}", "(", ")"
        )
        return mathChars.any { text.contains(it) } || text.matches(Regex("^[a-zA-Z][0-9a-zA-Z_\\^+-]*$"))
    }
}
