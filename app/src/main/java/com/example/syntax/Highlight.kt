package com.example.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object CodeHighlighter {

    // Theme Palette for Syntax Tokens
    val KeywordColor = Color(0xFFC678DD)      // Purple / Violet
    val TypeColor = Color(0xFF61AFEF)         // Light Blue / Cyan
    val StringColor = Color(0xFF98C379)       // Soft Green
    val NumberColor = Color(0xFFD19A66)       // Orange / Amber
    val CommentColor = Color(0xFF7F848E)      // Slate Muted Gray
    val AnnotationColor = Color(0xFFE5C07B)   // Yellow / Gold
    val FunctionColor = Color(0xFF56B6C2)     // Cyan / Teal
    val OperatorColor = Color(0xFFE06C75)     // Coral / Red
    val DefaultTextColor = Color(0xFFABB2BF)  // Crisp Off-White

    fun highlight(code: String, language: String): AnnotatedString {
        val lang = language.lowercase().trim()
        val keywords = getKeywordsForLanguage(lang)
        val types = getTypesForLanguage(lang)

        return buildAnnotatedString {
            append(code)

            // 1. Strings: "...", '...', `...`
            applyRegex(code, Regex("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'|`(\\\\.|[^`\\\\])*`"), SpanStyle(color = StringColor))

            // 2. Comments: single line //, #, -- and multi line /* ... */
            applyRegex(code, Regex("//[^\n]*|#[^\n]*|--[^\n]*"), SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic))
            applyRegex(code, Regex("/\\*[\\s\\S]*?\\*/"), SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic))

            // 3. Numbers: hex, float, integer
            applyRegex(code, Regex("\\b(0x[0-9a-fA-F]+|\\d+\\.?\\d*([eE][+-]?\\d+)?)[fFdDlL]?\\b"), SpanStyle(color = NumberColor))

            // 4. Annotations / Decorators: @Name
            applyRegex(code, Regex("@[a-zA-Z_][a-zA-Z0-9_.]*"), SpanStyle(color = AnnotationColor))

            // 5. Types & Classes: PascalCase words or explicit language built-ins
            if (types.isNotEmpty()) {
                val typePattern = "\\b(" + types.joinToString("|") { Regex.escape(it) } + ")\\b"
                applyRegex(code, Regex(typePattern), SpanStyle(color = TypeColor, fontWeight = FontWeight.SemiBold))
            } else {
                applyRegex(code, Regex("\\b[A-Z][a-zA-Z0-9_]+\\b"), SpanStyle(color = TypeColor, fontWeight = FontWeight.SemiBold))
            }

            // 6. Keywords
            if (keywords.isNotEmpty()) {
                val kwPattern = "\\b(" + keywords.joinToString("|") { Regex.escape(it) } + ")\\b"
                applyRegex(code, Regex(kwPattern), SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
            }

            // 7. Functions: word followed by (
            applyRegex(code, Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()"), SpanStyle(color = FunctionColor))

            // 8. Operators and punctuation
            applyRegex(code, Regex("[=+\\-*/%&|^!<>?:~]"), SpanStyle(color = OperatorColor))

            // Special rules for HTML / XML / SVG
            if (lang in listOf("html", "xml", "svg")) {
                applyRegex(code, Regex("</?[a-zA-Z0-9_-]+"), SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
                applyRegex(code, Regex("[a-zA-Z0-9_-]+(?==)"), SpanStyle(color = TypeColor))
            }

            // Special rules for JSON
            if (lang == "json") {
                applyRegex(code, Regex("\"[^\"]+\"\\s*(?=:)"), SpanStyle(color = TypeColor, fontWeight = FontWeight.SemiBold))
            }

            // Special rules for SQL
            if (lang == "sql") {
                applyRegex(code, Regex("\\b(SELECT|FROM|WHERE|INSERT|INTO|UPDATE|DELETE|JOIN|LEFT|RIGHT|INNER|OUTER|GROUP|BY|ORDER|HAVING|LIMIT|CREATE|TABLE|DROP|ALTER|PRIMARY|KEY|NOT|NULL|DEFAULT|CASCADE)\\b", RegexOption.IGNORE_CASE), SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
            }

            // Special rules for Mermaid
            if (lang == "mermaid") {
                applyRegex(code, Regex("\\b(graph|flowchart|subgraph|end|sequenceDiagram|classDiagram|stateDiagram|erDiagram|pie|gantt|gitGraph|mindmap|TD|TB|BT|RL|LR)\\b"), SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold))
                applyRegex(code, Regex("(-->|==>|-\\.->|--|==|:::)"), SpanStyle(color = OperatorColor))
            }
        }
    }

    private fun AnnotatedString.Builder.applyRegex(code: String, regex: Regex, style: SpanStyle) {
        for (match in regex.findAll(code)) {
            addStyle(style, match.range.first, match.range.last + 1)
        }
    }

    private fun getKeywordsForLanguage(lang: String): List<String> {
        return when (lang) {
            "kotlin", "kt" -> listOf(
                "package", "import", "class", "interface", "fun", "val", "var", "is", "in", "as",
                "this", "super", "when", "if", "else", "try", "catch", "finally", "for", "do",
                "while", "throw", "return", "continue", "break", "null", "true", "false",
                "object", "companion", "data", "sealed", "open", "override", "private", "public",
                "protected", "internal", "abstract", "suspend", "inline", "crossinline", "noinline"
            )
            "java" -> listOf(
                "package", "import", "class", "interface", "enum", "extends", "implements", "public",
                "private", "protected", "static", "final", "void", "return", "if", "else", "for",
                "while", "do", "switch", "case", "default", "break", "continue", "new", "this",
                "super", "throw", "throws", "try", "catch", "finally", "instanceof", "synchronized",
                "volatile", "transient", "native", "abstract", "strictfp", "assert", "true", "false", "null"
            )
            "python", "py" -> listOf(
                "def", "class", "import", "from", "as", "return", "if", "elif", "else", "while",
                "for", "in", "try", "except", "finally", "raise", "with", "pass", "break", "continue",
                "and", "or", "not", "is", "lambda", "yield", "global", "nonlocal", "assert", "async",
                "await", "True", "False", "None"
            )
            "javascript", "js", "typescript", "ts" -> listOf(
                "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
                "switch", "case", "default", "break", "continue", "new", "this", "class", "extends",
                "super", "import", "export", "from", "default", "try", "catch", "finally", "throw",
                "typeof", "instanceof", "in", "of", "void", "delete", "await", "async", "yield",
                "true", "false", "null", "undefined", "interface", "type", "enum", "implements"
            )
            "rust", "rs" -> listOf(
                "fn", "let", "mut", "pub", "struct", "enum", "trait", "impl", "use", "mod", "crate",
                "match", "if", "else", "loop", "while", "for", "in", "return", "break", "continue",
                "as", "where", "move", "unsafe", "ref", "self", "Self", "true", "false", "Some", "None",
                "Ok", "Err", "async", "await", "const", "static", "type"
            )
            "go", "golang" -> listOf(
                "package", "import", "func", "return", "var", "const", "type", "struct", "interface",
                "if", "else", "switch", "case", "default", "for", "range", "break", "continue",
                "fallthrough", "go", "defer", "chan", "select", "map", "true", "false", "nil"
            )
            "c", "cpp", "c++" -> listOf(
                "include", "define", "ifdef", "ifndef", "endif", "auto", "break", "case", "char",
                "const", "continue", "default", "do", "double", "else", "enum", "extern", "float",
                "for", "goto", "if", "int", "long", "register", "return", "short", "signed", "sizeof",
                "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile",
                "while", "class", "public", "private", "protected", "virtual", "template", "typename",
                "new", "delete", "namespace", "using", "true", "false", "nullptr"
            )
            "csharp", "c#" -> listOf(
                "using", "namespace", "class", "struct", "interface", "enum", "delegate", "public",
                "private", "protected", "internal", "static", "readonly", "volatile", "virtual",
                "override", "abstract", "sealed", "async", "await", "var", "new", "return", "if",
                "else", "switch", "case", "default", "for", "foreach", "while", "do", "break",
                "continue", "try", "catch", "finally", "throw", "true", "false", "null"
            )
            "swift" -> listOf(
                "import", "func", "var", "let", "class", "struct", "enum", "protocol", "extension",
                "init", "deinit", "subscript", "typealias", "associatedtype", "if", "guard", "else",
                "switch", "case", "default", "for", "in", "while", "repeat", "return", "throw",
                "throws", "rethrows", "try", "catch", "break", "continue", "self", "Self", "nil", "true", "false"
            )
            "bash", "sh", "zsh", "shell" -> listOf(
                "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until", "do",
                "done", "in", "function", "select", "time", "return", "exit", "echo", "export",
                "source", "local", "readonly", "set", "unset", "alias"
            )
            "yaml", "yml" -> listOf(
                "true", "false", "yes", "no", "on", "off", "null"
            )
            "ruby", "rb" -> listOf(
                "def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until",
                "for", "in", "do", "begin", "rescue", "ensure", "raise", "return", "yield", "self",
                "super", "true", "false", "nil", "require", "include"
            )
            "dart" -> listOf(
                "import", "export", "library", "part", "class", "abstract", "extends", "with",
                "implements", "var", "final", "const", "late", "void", "return", "if", "else",
                "for", "while", "do", "switch", "case", "default", "break", "continue", "try",
                "catch", "finally", "throw", "rethrow", "async", "await", "yield", "true", "false", "null"
            )
            "php" -> listOf(
                "php", "echo", "print", "function", "return", "class", "interface", "trait", "extends",
                "implements", "public", "private", "protected", "static", "final", "abstract",
                "if", "else", "elseif", "for", "foreach", "while", "do", "switch", "case", "default",
                "break", "continue", "try", "catch", "finally", "throw", "new", "clone", "true", "false", "null"
            )
            else -> listOf(
                "function", "def", "fun", "fn", "var", "val", "let", "const", "return", "if", "else",
                "for", "while", "class", "interface", "import", "package", "true", "false", "null"
            )
        }
    }

    private fun getTypesForLanguage(lang: String): List<String> {
        return when (lang) {
            "kotlin", "kt" -> listOf("Int", "Long", "Float", "Double", "String", "Boolean", "Char", "Byte", "Short", "Unit", "Any", "List", "Set", "Map", "MutableList", "StateFlow", "SharedFlow", "ViewModel", "Composable")
            "java" -> listOf("int", "long", "float", "double", "boolean", "char", "byte", "short", "String", "Object", "List", "ArrayList", "Map", "HashMap", "Set", "HashSet", "Integer", "Boolean", "Double")
            "python", "py" -> listOf("int", "float", "str", "bool", "list", "dict", "set", "tuple", "bytes", "Optional", "Union", "Any", "Callable", "List", "Dict")
            "typescript", "ts" -> listOf("string", "number", "boolean", "any", "void", "never", "unknown", "object", "Array", "Promise", "Record", "Partial", "Required")
            "rust", "rs" -> listOf("i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64", "bool", "char", "str", "String", "Vec", "Option", "Result", "Box", "Rc", "Arc")
            "go", "golang" -> listOf("int", "int8", "int16", "int32", "int64", "uint", "uint8", "uint16", "uint32", "uint64", "float32", "float64", "complex64", "complex128", "string", "bool", "byte", "rune", "error")
            "c", "cpp", "c++" -> listOf("int", "char", "float", "double", "bool", "void", "short", "long", "unsigned", "signed", "size_t", "string", "vector", "map", "set", "pair", "unique_ptr", "shared_ptr")
            "csharp", "c#" -> listOf("int", "long", "float", "double", "decimal", "string", "bool", "char", "byte", "short", "object", "void", "Task", "List", "Dictionary")
            "swift" -> listOf("Int", "Double", "Float", "String", "Bool", "Character", "Array", "Dictionary", "Set", "Optional")
            else -> emptyList()
        }
    }
}
