package com.example

import com.example.model.ContentBlock
import com.example.parser.MarkdownParser
import com.example.syntax.CodeHighlighter
import com.example.ui.components.LatexFormatter
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testInlineFormulaDetection() {
        val text = "The equation \$x^2 + y^2 = r^2\$ is for a circle, and \$E = mc^2\$ is energy."
        val spans = MarkdownParser.extractInlineFormulas(text)

        assertEquals(2, spans.size)
        assertEquals("x^2 + y^2 = r^2", spans[0].formula)
        assertEquals("E = mc^2", spans[1].formula)
    }

    @Test
    fun testMarkdownBlockParsing() {
        val markdown = """
            Here is a theorem:
            
            $$
            \int_{0}^{\infty} e^{-x^2} dx = \frac{\sqrt{\pi}}{2}
            $$
            
            And here is a code block:
            ```kotlin
            fun solve(): Int = 42
            ```
            
            And a diagram:
            ```mermaid
            graph TD
                A --> B
            ```
            
            And an SVG:
            ```svg
            <svg><circle cx="50" cy="50" r="40"/></svg>
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdown)
        assertTrue(blocks.any { it is ContentBlock.TextBlock })
        assertTrue(blocks.any { it is ContentBlock.FormulaBlock })
        assertTrue(blocks.any { it is ContentBlock.CodeBlock && it.language == "kotlin" })
        assertTrue(blocks.any { it is ContentBlock.MermaidBlock })
        assertTrue(blocks.any { it is ContentBlock.SvgBlock })
    }

    @Test
    fun testLatexFormatting() {
        val formatted = LatexFormatter.formatMathAnnotated("x^2 + \\alpha = \\frac{a}{b}")
        val str = formatted.text
        assertTrue("Expected superscript 2", str.contains("x²"))
        assertTrue("Expected alpha symbol", str.contains("α"))
        assertTrue("Expected fraction layout", str.contains("(a / b)"))
    }

    @Test
    fun testSyntaxHighlighter() {
        val highlighted = CodeHighlighter.highlight("fun main() { val x = 42 }", "kotlin")
        assertNotNull(highlighted)
        assertTrue(highlighted.text.contains("fun"))
    }
}
