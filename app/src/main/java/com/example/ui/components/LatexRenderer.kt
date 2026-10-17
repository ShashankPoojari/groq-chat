package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Functions
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ContentBlock

object LatexFormatter {

    private val GREEK_MAP = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
        "\\pi" to "π", "\\varpi" to "ϖ", "\\rho" to "ρ", "\\varrho" to "ϱ",
        "\\sigma" to "σ", "\\varsigma" to "ς", "\\tau" to "τ", "\\upsilon" to "υ",
        "\\phi" to "φ", "\\varphi" to "ϕ", "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
        "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Upsilon" to "Υ",
        "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω"
    )

    private val SYMBOLS_MAP = mapOf(
        "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
        "\\le" to "≤", "\\leq" to "≤", "\\ge" to "≥", "\\geq" to "≥",
        "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
        "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇",
        "\\int" to "∫", "\\iint" to "∬", "\\sum" to "∑", "\\prod" to "∏",
        "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂", "\\subseteq" to "⊆",
        "\\cup" to "∪", "\\cap" to "∩", "\\forall" to "∀", "\\exists" to "∃",
        "\\to" to "→", "\\rightarrow" to "→", "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐", "\\Leftrightarrow" to "⇔",
        "\\cdot" to "·", "\\ldots" to "…", "\\cdots" to "⋯", "\\hbar" to "ℏ"
    )

    private val SUPERSCRIPT_MAP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 't' to 'ᵗ'
    )

    private val SUBSCRIPT_MAP = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'i' to 'ᵢ',
        'j' to 'ⱼ', 'k' to 'ₖ', 'm' to 'ₘ', 'n' to 'ₙ', 'p' to 'ₚ', 't' to 'ₜ'
    )

    /**
     * Typesets a LaTeX math formula into a polished mathematical representation
     * with italic variables, true superscripts/subscripts, fractions, and Greek/symbol fonts.
     */
    fun formatMathAnnotated(latex: String, isDisplay: Boolean = false): AnnotatedString {
        var processed = latex.trim()

        // 1. Fractions: \frac{a}{b} -> (a)/(b) or stylized fraction
        processed = Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}").replace(processed) { mr ->
            val num = mr.groupValues[1]
            val den = mr.groupValues[2]
            "($num / $den)"
        }

        // 2. Square roots: \sqrt{x} -> √(x)
        processed = Regex("\\\\sqrt\\[([^\\]]+)\\]\\{([^}]+)\\}").replace(processed) { mr ->
            "${mr.groupValues[1]}√(${mr.groupValues[2]})"
        }
        processed = Regex("\\\\sqrt\\{([^}]+)\\}").replace(processed) { mr ->
            "√(${mr.groupValues[1]})"
        }

        // 3. Greek and mathematical symbols
        for ((tex, sym) in GREEK_MAP) {
            processed = processed.replace(tex, sym)
        }
        for ((tex, sym) in SYMBOLS_MAP) {
            processed = processed.replace(tex, sym)
        }

        // 4. Superscripts: x^{2} or x^2
        processed = Regex("\\^\\{([^}]+)\\}|\\^([0-9a-zA-Z+-])").replace(processed) { mr ->
            val content = mr.groupValues[1].ifEmpty { mr.groupValues[2] }
            content.map { SUPERSCRIPT_MAP[it] ?: it }.joinToString("")
        }

        // 5. Subscripts: x_{i} or x_i
        processed = Regex("_\\{([^}]+)\\}|_([0-9a-zA-Z+-])").replace(processed) { mr ->
            val content = mr.groupValues[1].ifEmpty { mr.groupValues[2] }
            content.map { SUBSCRIPT_MAP[it] ?: it }.joinToString("")
        }

        // Clean leftover TeX commands
        processed = processed.replace("\\left", "").replace("\\right", "")
        processed = processed.replace("\\text{", "").replace("}", "")
        processed = processed.replace("\\,", " ").replace("\\;", " ").replace("\\!", "")

        return buildAnnotatedString {
            // Italicize single-letter variables: x, y, z, a, b, etc.
            val singleLetterVar = Regex("(?<![a-zA-Z])([a-zA-Z])(?![a-zA-Z])")
            append(processed)

            for (match in singleLetterVar.findAll(processed)) {
                val ch = match.value
                if (ch != "d" || match.range.first == 0) { // e.g. dx in integrals
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }
        }
    }
}

/**
 * Interactive typeset badge for inline formulas inside sentences ($x^2$).
 * Tapping opens the Full-Screen Formula Viewer!
 */
@Composable
fun InlineFormulaChip(
    formula: String,
    onExpandFormula: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatted = remember(formula) { LatexFormatter.formatMathAnnotated(formula) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onExpandFormula(formula) }
            .testTag("inline_formula_chip")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Functions,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 2.dp)
            )
            Text(
                text = formatted,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Display card for standalone block formulas ($$ ... $$)
 * Includes typeset rendering, Copy LaTeX button, and Full-Screen expand button.
 */
@Composable
fun BlockFormulaCard(
    formula: ContentBlock.FormulaBlock,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val formattedMath = remember(formula.formula) { LatexFormatter.formatMathAnnotated(formula.formula, isDisplay = true) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
            .testTag("block_formula_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = "Formula",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Equation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(formula.formula))
                            copied = true
                        },
                        modifier = Modifier.size(28.dp).testTag("copy_formula_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy LaTeX",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(28.dp).testTag("expand_formula_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full-screen Formula",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Typeset Math content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .clickable { onExpand() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formattedMath,
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
