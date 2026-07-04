@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.config.AppConfig
import kotlin.math.roundToInt

@Composable
internal fun SettingsExecutionAndAppearanceSection(
    config: AppConfig,
    contextLength: Int,
    loadLimit: String,
    maxParallelSubAgents: String,
    timeoutSeconds: String,
    streamingEnabled: Boolean,
    thinkingEnabled: Boolean,
    autoCompactionEnabled: Boolean,
    userInstruction: String,
    fontSize: Int,
    onContextLengthChange: (Int) -> Unit,
    onLoadLimitChange: (String) -> Unit,
    onMaxParallelChange: (String) -> Unit,
    onTimeoutChange: (String) -> Unit,
    onStreamingChange: (Boolean) -> Unit,
    onThinkingChange: (Boolean) -> Unit,
    onCompactionChange: (Boolean) -> Unit,
    onUserInstructionChange: (String) -> Unit,
    onFontSizeChange: (Int) -> Unit,
) {
    PanelSection(title = "Execution") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Context length", color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
                Slider(
                    value = contextLength.toFloat(),
                    onValueChange = {
                        val snapped = ((it / 1024f).roundToInt() * 1024).coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH)
                        onContextLengthChange(snapped)
                    },
                    valueRange = MIN_CONTEXT_LENGTH.toFloat()..MAX_CONTEXT_LENGTH.toFloat(),
                    steps = ((MAX_CONTEXT_LENGTH - MIN_CONTEXT_LENGTH) / 1024) - 1,
                )
            }
            Text("$contextLength", color = Color(0xFFBD93F9), modifier = Modifier.width(72.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NumericPanelField(
                label = "Startup history",
                value = loadLimit,
                onValueChange = onLoadLimitChange,
                modifier = Modifier.weight(1f),
            )
            NumericPanelField(
                label = "Parallel agents",
                value = maxParallelSubAgents,
                onValueChange = onMaxParallelChange,
                modifier = Modifier.weight(1f),
            )
            NumericPanelField(
                label = "Timeout sec",
                value = timeoutSeconds,
                onValueChange = onTimeoutChange,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelCheckbox(label = "Stream", checked = streamingEnabled, onCheckedChange = onStreamingChange)
            PanelCheckbox(label = "Reasoning", checked = thinkingEnabled, onCheckedChange = onThinkingChange)
            PanelCheckbox(label = "Compaction", checked = autoCompactionEnabled, onCheckedChange = onCompactionChange)
        }
        OutlinedTextField(
            value = userInstruction,
            onValueChange = onUserInstructionChange,
            label = { Text("Model instruction") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    PanelSection(title = "Appearance") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Font size", color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = {
                        val newSize = it.roundToInt().clampFontSize()
                        onFontSizeChange(newSize)
                    },
                    valueRange = MIN_SETTINGS_FONT_SIZE.toFloat()..MAX_SETTINGS_FONT_SIZE.toFloat(),
                    steps = MAX_SETTINGS_FONT_SIZE - MIN_SETTINGS_FONT_SIZE - 1,
                )
            }
            Text("$fontSize px", color = Color(0xFFBD93F9), modifier = Modifier.width(48.dp))
        }
    }
}
