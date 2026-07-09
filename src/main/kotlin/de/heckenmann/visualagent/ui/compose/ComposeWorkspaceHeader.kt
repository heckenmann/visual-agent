@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeWorkspaceHeader(
    providerName: String,
    modelName: String,
    beanDefinitionCount: Int,
    inFlight: InFlightState,
    onStopAll: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Visual Agent",
                color = Color(0xFFF8F8F2),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Compose Multiplatform workspace",
                color = Color(0xFFBFBBD0),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HeaderChip("Provider", providerName)
        HeaderChip("Model", modelName)
        HeaderChip("Beans", beanDefinitionCount.toString())
        InFlightIndicator(state = inFlight, onStopAll = onStopAll)
    }
}

@Composable
internal fun PanelStatus(status: String) {
    Text(status, color = Color(0xFF8BE9FD), style = MaterialTheme.typography.bodySmall)
}

internal fun ComposeWorkspaceWindow.railIcon(): ImageVector =
    when (id) {
        "chat" -> Icons.AutoMirrored.Filled.Chat
        "todos" -> Icons.Filled.CheckCircle
        "files" -> Icons.Filled.Folder
        "agents" -> Icons.Filled.Group
        "settings" -> Icons.Filled.Settings
        "canvas" -> Icons.Filled.Brush
        else -> Icons.Filled.Description
    }

internal const val MIN_PANEL_WIDTH = 200
internal const val MAX_PANEL_WIDTH = 2400

@Composable
internal fun HorizontalDividerLine() {
    HorizontalDivider(color = Color(0x33444A65), modifier = Modifier.padding(vertical = 10.dp))
}

@Composable
internal fun PanelWidthSlider(
    current: Int,
    onWidthChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderValue by remember(current) { mutableFloatStateOf(current.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "${sliderValue.toInt()} px",
            color = Color(0xFFF8F8F2),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onWidthChange(sliderValue.toInt().coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH)) },
            valueRange = MIN_PANEL_WIDTH.toFloat()..MAX_PANEL_WIDTH.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(
                icon = Icons.Filled.Close,
                description = "Cancel",
                onClick = onDismiss,
            )
            ActionIconButton(
                icon = Icons.Filled.CheckCircle,
                description = "Apply width",
                onClick = {
                    onWidthChange(sliderValue.toInt().coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH))
                    onDismiss()
                },
            )
        }
    }
}
