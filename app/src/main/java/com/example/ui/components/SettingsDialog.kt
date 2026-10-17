package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf(currentSettings.groqApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(currentSettings.selectedModel) }
    var temperature by remember { mutableFloatStateOf(currentSettings.temperature) }
    var systemPrompt by remember { mutableStateOf(currentSettings.systemPrompt) }
    var showLineNumbers by remember { mutableStateOf(currentSettings.showLineNumbersByDefault) }
    var wordWrap by remember { mutableStateOf(currentSettings.wordWrapByDefault) }

    val scrollState = rememberScrollState()

    val models = listOf(
        "llama-3.3-70b-versatile" to "Llama 3.3 70B (Recommended)",
        "llama-3.1-8b-instant" to "Llama 3.1 8B (Ultra Fast)",
        "mixtral-8x7b-32768" to "Mixtral 8x7B (MoE)",
        "gemma2-9b-it" to "Gemma 2 9B (Google on Groq)",
        "gemini-1.5-flash" to "Gemini 1.5 Flash (Fallback)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GroqChat Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Groq API Key
                Text(
                    text = "Groq API Key",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Get a free key instantly at console.groq.com/keys",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = { Text("gsk_...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // AI Model Selection
                Text(
                    text = "AI Model",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                models.forEach { (modelId, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedModel == modelId,
                            onClick = { selectedModel = modelId }
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Temperature Slider
                Text(
                    text = "Creativity (Temperature: ${String.format("%.2f", temperature)})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.0f..1.5f,
                    steps = 15,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Code Display Defaults
                Text(
                    text = "Code & Formula Preferences",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Line Numbers in Code", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showLineNumbers, onCheckedChange = { showLineNumbers = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Word Wrap in Code", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = wordWrap, onCheckedChange = { wordWrap = it })
                }

                Spacer(modifier = Modifier.height(18.dp))

                // System Prompt
                Text(
                    text = "System Prompt",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSaveSettings(
                                currentSettings.copy(
                                    groqApiKey = apiKey.trim(),
                                    selectedModel = selectedModel,
                                    temperature = temperature,
                                    systemPrompt = systemPrompt,
                                    showLineNumbersByDefault = showLineNumbers,
                                    wordWrapByDefault = wordWrap
                                )
                            )
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_settings_button")
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
