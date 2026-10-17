package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStateChange: (Boolean) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available on this device")
            return
        }

        stopListening()

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onStateChange(true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    onStateChange(false)
                }

                override fun onError(error: Int) {
                    isListening = false
                    onStateChange(false)
                    onError("Speech error code: $error")
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onStateChange(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            onStateChange(false)
            onError(e.message ?: "Failed to start speech listener")
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        } catch (e: Exception) {
            // ignore
        }
        isListening = false
        onStateChange(false)
    }
}

class TtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    var isSpeaking: Boolean = false
        private set

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    fun speak(text: String, onComplete: () -> Unit = {}) {
        stop()
        val plainText = stripMarkdown(text)
        isSpeaking = true
        tts?.speak(plainText, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun stripMarkdown(markdown: String): String {
        return markdown
            .replace(Regex("```[\\s\\S]*?```"), " code block omitted ")
            .replace(Regex("\\$\\$[\\s\\S]*?\\$\\$"), " formula omitted ")
            .replace(Regex("<svg[\\s\\S]*?</svg>"), " vector graphic omitted ")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("#+\\s*"), "")
    }
}
