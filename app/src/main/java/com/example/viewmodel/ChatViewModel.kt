package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GroqApiClient
import com.example.audio.SpeechHelper
import com.example.audio.TtsHelper
import com.example.data.ChatRepository
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val apiClient = GroqApiClient()

    private val _settings = MutableStateFlow(repository.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _sessions = MutableStateFlow(repository.loadSessions())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow(_sessions.value.firstOrNull()?.id ?: "")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    val currentSession: StateFlow<ChatSession?> = combine(_sessions, _currentSessionId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _sessions.value.firstOrNull())

    val inputText = MutableStateFlow("")
    val isGenerating = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val showSettingsDialog = MutableStateFlow(false)

    // Full-Screen Dialog states
    val fullScreenCodeBlock = MutableStateFlow<ContentBlock.CodeBlock?>(null)
    val fullScreenFormulaBlock = MutableStateFlow<ContentBlock.FormulaBlock?>(null)
    val fullScreenMermaidBlock = MutableStateFlow<ContentBlock.MermaidBlock?>(null)
    val fullScreenSvgBlock = MutableStateFlow<ContentBlock.SvgBlock?>(null)

    // Speech and TTS
    val isListeningSpeech = MutableStateFlow(false)
    val speakingMessageContent = MutableStateFlow<String?>(null)

    private val ttsHelper = TtsHelper(application)
    private val speechHelper = SpeechHelper(
        context = application,
        onResult = { recognizedText ->
            val cur = inputText.value
            inputText.value = if (cur.isBlank()) recognizedText else "$cur $recognizedText"
        },
        onError = { err ->
            errorMessage.value = err
        },
        onStateChange = { active ->
            isListeningSpeech.value = active
        }
    )

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        repository.saveSettings(newSettings)
    }

    fun selectSession(id: String) {
        _currentSessionId.value = id
    }

    fun createNewSession() {
        val newSession = ChatSession(title = "New Chat")
        _sessions.update { listOf(newSession) + it }
        _currentSessionId.value = newSession.id
        repository.saveSessions(_sessions.value)
    }

    fun deleteSession(id: String) {
        _sessions.update { list ->
            val filtered = list.filter { it.id != id }
            if (filtered.isEmpty()) listOf(ChatSession(title = "New Chat")) else filtered
        }
        if (_currentSessionId.value == id) {
            _currentSessionId.value = _sessions.value.first().id
        }
        repository.saveSessions(_sessions.value)
    }

    fun clearCurrentSession() {
        val curId = _currentSessionId.value
        _sessions.update { list ->
            list.map { if (it.id == curId) it.copy(messages = emptyList()) else it }
        }
        repository.saveSessions(_sessions.value)
    }

    fun sendMessage(explicitPrompt: String? = null) {
        val text = (explicitPrompt ?: inputText.value).trim()
        if (text.isEmpty() || isGenerating.value) return

        val userMessage = ChatMessage(role = MessageRole.USER, content = text)
        inputText.value = ""
        errorMessage.value = null

        val curId = _currentSessionId.value
        val curSession = _sessions.value.find { it.id == curId } ?: return

        // Auto-title session if first message
        val updatedTitle = if (curSession.messages.isEmpty() || curSession.title == "New Chat") {
            text.take(30) + (if (text.length > 30) "..." else "")
        } else {
            curSession.title
        }

        val assistantPlaceholder = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            model = _settings.value.selectedModel
        )

        val updatedMessages = curSession.messages + userMessage + assistantPlaceholder
        _sessions.update { list ->
            list.map {
                if (it.id == curId) it.copy(title = updatedTitle, messages = updatedMessages, updatedAt = System.currentTimeMillis())
                else it
            }
        }
        repository.saveSessions(_sessions.value)

        isGenerating.value = true

        viewModelScope.launch {
            val responseResult = apiClient.sendChatCompletion(
                apiKey = _settings.value.groqApiKey,
                model = _settings.value.selectedModel,
                messages = curSession.messages + userMessage,
                systemPrompt = _settings.value.systemPrompt,
                temperature = _settings.value.temperature,
                maxTokens = _settings.value.maxTokens
            )

            isGenerating.value = false

            responseResult.onSuccess { response ->
                val finalizedAssistantMessage = assistantPlaceholder.copy(
                    content = response.content,
                    isStreaming = false,
                    model = response.model,
                    tokensPerSecond = response.tokensPerSecond
                )
                _sessions.update { list ->
                    list.map { session ->
                        if (session.id == curId) {
                            val msgs = session.messages.dropLast(1) + finalizedAssistantMessage
                            session.copy(messages = msgs, updatedAt = System.currentTimeMillis())
                        } else session
                    }
                }
                repository.saveSessions(_sessions.value)
            }.onFailure { err ->
                val errorMsg = err.message ?: "Failed to generate response"
                errorMessage.value = errorMsg
                val failedAssistantMessage = assistantPlaceholder.copy(
                    content = "⚠️ $errorMsg",
                    isStreaming = false,
                    error = errorMsg
                )
                _sessions.update { list ->
                    list.map { session ->
                        if (session.id == curId) {
                            val msgs = session.messages.dropLast(1) + failedAssistantMessage
                            session.copy(messages = msgs)
                        } else session
                    }
                }
                repository.saveSessions(_sessions.value)
            }
        }
    }

    fun regenerateLastResponse() {
        val curSession = currentSession.value ?: return
        val lastUserMessage = curSession.messages.findLast { it.role == MessageRole.USER } ?: return

        // Drop trailing assistant messages
        val trimmedMessages = curSession.messages.takeWhile { it.id != lastUserMessage.id }
        _sessions.update { list ->
            list.map { if (it.id == curSession.id) it.copy(messages = trimmedMessages) else it }
        }
        sendMessage(lastUserMessage.content)
    }

    fun toggleSpeechRecognition() {
        if (isListeningSpeech.value) {
            speechHelper.stopListening()
        } else {
            speechHelper.startListening()
        }
    }

    fun speakText(text: String) {
        if (speakingMessageContent.value == text) {
            ttsHelper.stop()
            speakingMessageContent.value = null
        } else {
            speakingMessageContent.value = text
            ttsHelper.speak(text) {
                speakingMessageContent.value = null
            }
        }
    }

    fun insertFormulaSnippet() {
        val cur = inputText.value
        inputText.value = if (cur.isEmpty()) "Calculate the formula \$x^2 + y^2 = z^2\$" else "$cur \$x^2\$"
    }

    fun insertDiagramSnippet() {
        val cur = inputText.value
        inputText.value = if (cur.isEmpty()) "Generate a Mermaid diagram for user login architecture" else "$cur \nCreate a Mermaid diagram"
    }

    override fun onCleared() {
        super.onCleared()
        speechHelper.stopListening()
        ttsHelper.destroy()
    }
}
