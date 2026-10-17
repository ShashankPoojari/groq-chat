package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject

class ChatRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("groqchat_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        return AppSettings(
            groqApiKey = prefs.getString("groq_api_key", "") ?: "",
            selectedModel = prefs.getString("selected_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile",
            temperature = prefs.getFloat("temperature", 0.7f),
            maxTokens = prefs.getInt("max_tokens", 4096),
            systemPrompt = prefs.getString("system_prompt", AppSettings().systemPrompt) ?: AppSettings().systemPrompt,
            formulaRenderMode = prefs.getString("formula_render_mode", "KaTeX") ?: "KaTeX",
            showLineNumbersByDefault = prefs.getBoolean("show_line_numbers", true),
            wordWrapByDefault = prefs.getBoolean("word_wrap", false),
            codeFontSizeSp = prefs.getFloat("code_font_size", 13f),
            hapticFeedback = prefs.getBoolean("haptic", true),
            voiceTtsEnabled = prefs.getBoolean("voice_tts", true)
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString("groq_api_key", settings.groqApiKey)
            .putString("selected_model", settings.selectedModel)
            .putFloat("temperature", settings.temperature)
            .putInt("max_tokens", settings.maxTokens)
            .putString("system_prompt", settings.systemPrompt)
            .putString("formula_render_mode", settings.formulaRenderMode)
            .putBoolean("show_line_numbers", settings.showLineNumbersByDefault)
            .putBoolean("word_wrap", settings.wordWrapByDefault)
            .putFloat("code_font_size", settings.codeFontSizeSp)
            .putBoolean("haptic", settings.hapticFeedback)
            .putBoolean("voice_tts", settings.voiceTtsEnabled)
            .apply()
    }

    fun loadSessions(): List<ChatSession> {
        val jsonStr = prefs.getString("chat_sessions", null) ?: return listOf(createWelcomeSession())
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<ChatSession>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                val isPinned = obj.optBoolean("isPinned", false)

                val msgArr = obj.getJSONArray("messages")
                val messages = mutableListOf<ChatMessage>()
                for (j in 0 until msgArr.length()) {
                    val mObj = msgArr.getJSONObject(j)
                    messages.add(
                        ChatMessage(
                            id = mObj.getString("id"),
                            role = MessageRole.valueOf(mObj.getString("role")),
                            content = mObj.getString("content"),
                            timestamp = mObj.optLong("timestamp", System.currentTimeMillis()),
                            model = if (mObj.has("model")) mObj.getString("model") else null,
                            tokensPerSecond = if (mObj.has("tps")) mObj.getDouble("tps") else null,
                            isStreaming = false
                        )
                    )
                }
                list.add(ChatSession(id, title, createdAt, updatedAt, messages, isPinned))
            }
            if (list.isEmpty()) listOf(createWelcomeSession()) else list
        } catch (e: Exception) {
            listOf(createWelcomeSession())
        }
    }

    fun saveSessions(sessions: List<ChatSession>) {
        try {
            val arr = JSONArray()
            sessions.forEach { s ->
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                    put("isPinned", s.isPinned)

                    val msgArr = JSONArray()
                    s.messages.forEach { m ->
                        msgArr.put(JSONObject().apply {
                            put("id", m.id)
                            put("role", m.role.name)
                            put("content", m.content)
                            put("timestamp", m.timestamp)
                            m.model?.let { put("model", it) }
                            m.tokensPerSecond?.let { put("tps", it) }
                        })
                    }
                    put("messages", msgArr)
                }
                arr.put(obj)
            }
            prefs.edit().putString("chat_sessions", arr.toString()).apply()
        } catch (e: Exception) {
            // Log or ignore
        }
    }

    private fun createWelcomeSession(): ChatSession {
        val welcomeMessages = listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = """
# Welcome to GroqChat AI ⚡

I am ready to assist you with lightning-fast AI inference, mathematical reasoning, software development, and visual diagrams.

### 📐 Typeset Inline & Block Formulas
Notice how inline formulas like ${'$'}x^2 + y^2 = r^2${'$'} and ${'$'}E = mc^2${'$'} are typeset cleanly inside sentences.
You can tap on any inline formula chip to launch the **Full-screen Formula Viewer**!

Here is the famous Gaussian bell-curve integral:

$$\int_{-\infty}^{\infty} e^{-x^2} \, dx = \sqrt{\pi}$$

### 💻 Full-Screen Code Viewer & Multi-Language Coloring
```kotlin
// Thread-safe async worker with coroutines
class FastWorker(private val dispatcher: CoroutineDispatcher) {
    private val _status = MutableStateFlow<WorkStatus>(WorkStatus.Idle)
    val status: StateFlow<WorkStatus> = _status.asStateFlow()

    suspend fun executeTask(payload: TaskPayload): Result<TaskOutput> = withContext(dispatcher) {
        _status.value = WorkStatus.Processing
        val hash = CryptoUtils.sha256(payload.rawBytes)
        println("Processed item with hash: ${'$'}hash")
        Result.success(TaskOutput(hash = hash, elapsedMs = 42L))
    }
}
```
*Tap the expand icon on the code header to open the interactive full-screen viewer with syntax coloring, search, font zoom, line numbers, and word wrap!*

### 📊 Mermaid Diagram
```mermaid
graph TD
    A[User Question] --> B{Formulas or Code?}
    B -->|Formulas| C[Typeset with KaTeX]
    B -->|Code| D[Syntax Highlighter]
    B -->|Diagrams| E[Mermaid & SVG]
    C --> F[Full-Screen Interactive Viewer]
    D --> F
    E --> F
```

### 🎨 SVG Scalable Vector Graphic
```svg
<svg viewBox="0 0 400 120" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#F97316;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#8B5CF6;stop-opacity:1" />
    </linearGradient>
  </defs>
  <rect x="10" y="10" width="380" height="100" rx="16" fill="url(#grad)" />
  <circle cx="60" cy="60" r="28" fill="#FFFFFF" fill-opacity="0.25" />
  <text x="60" y="68" font-family="sans-serif" font-size="24" font-weight="bold" fill="#FFFFFF" text-anchor="middle">⚡</text>
  <text x="110" y="55" font-family="sans-serif" font-size="18" font-weight="bold" fill="#FFFFFF">Groq High-Speed Engine</text>
  <text x="110" y="80" font-family="sans-serif" font-size="13" fill="#FFFFFF" fill-opacity="0.85">Sub-second latency &amp; hundreds of tokens/sec</text>
</svg>
```

Feel free to ask a question, request code in 20+ languages, or test any mathematical formulas!
                """.trimIndent(),
                tokensPerSecond = 512.0
            )
        )

        return ChatSession(
            title = "Welcome & Showcase",
            messages = welcomeMessages
        )
    }
}
