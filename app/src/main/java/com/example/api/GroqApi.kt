package com.example.api

import com.example.BuildConfig
import com.example.model.ChatMessage
import com.example.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendChatCompletion(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey.trim()
            if (key.isNotEmpty()) {
                // Call Groq API
                return@withContext callGroqApi(key, model, messages, systemPrompt, temperature, maxTokens)
            } else {
                // Fallback to Gemini API if Gemini API Key is available
                val geminiKey = try {
                    val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
                    val value = field.get(null) as? String ?: ""
                    if (value == "MY_GEMINI_API_KEY") "" else value
                } catch (e: Throwable) { "" }
                if (geminiKey.isNotBlank()) {
                    return@withContext callGeminiFallback(geminiKey, messages, systemPrompt)
                } else {
                    // Inform user to enter their Groq API Key or set Gemini Key
                    return@withContext Result.failure(
                        Exception("Please enter your Groq API key in Settings (tap ⚙️ at top right). Free keys are available instantly at console.groq.com/keys")
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGroqApi(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Result<ChatResponse> {
        val startTime = System.currentTimeMillis()

        val jsonMessages = JSONArray()

        if (systemPrompt.isNotBlank()) {
            jsonMessages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        messages.takeLast(16).forEach { msg ->
            if (msg.role == MessageRole.USER || msg.role == MessageRole.ASSISTANT) {
                jsonMessages.put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                    put("content", msg.content)
                })
            }
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", jsonMessages)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMessage = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: response.message
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                return Result.failure(Exception("Groq API error ($errMessage)"))
            }

            val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val content = firstChoice.getJSONObject("message").getString("content")

            val usage = json.optJSONObject("usage")
            val completionTokens = usage?.optInt("completion_tokens") ?: (content.length / 4)
            val tps = (completionTokens.toDouble() / (elapsedMs.toDouble() / 1000.0)).coerceAtLeast(1.0)

            return Result.success(
                ChatResponse(
                    content = content,
                    model = model,
                    tokens = completionTokens,
                    tokensPerSecond = tps,
                    latencyMs = elapsedMs
                )
            )
        }
    }

    private fun callGeminiFallback(
        geminiKey: String,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): Result<ChatResponse> {
        val startTime = System.currentTimeMillis()

        val contents = JSONArray()
        messages.takeLast(10).forEach { msg ->
            if (msg.role == MessageRole.USER || msg.role == MessageRole.ASSISTANT) {
                contents.put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", msg.content)
                    }))
                })
            }
        }

        val requestJson = JSONObject().apply {
            put("contents", contents)
            if (systemPrompt.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", systemPrompt) }))
                })
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return Result.failure(Exception("Gemini Fallback error (HTTP ${response.code}): $responseBody"))
            }

            val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            val estimatedTokens = text.length / 4
            val tps = (estimatedTokens.toDouble() / (elapsedMs.toDouble() / 1000.0)).coerceAtLeast(1.0)

            return Result.success(
                ChatResponse(
                    content = text,
                    model = "gemini-1.5-flash",
                    tokens = estimatedTokens,
                    tokensPerSecond = tps,
                    latencyMs = elapsedMs
                )
            )
        }
    }
}

data class ChatResponse(
    val content: String,
    val model: String,
    val tokens: Int,
    val tokensPerSecond: Double,
    val latencyMs: Long
)
