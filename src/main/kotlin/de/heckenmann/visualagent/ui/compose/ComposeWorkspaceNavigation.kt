@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.config.AppConfig

@Composable
internal fun ComposeRail(
    windows: List<ComposeWorkspaceWindow>,
    onToggleWindow: (String) -> Unit,
    onCloseApplication: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(76.dp)
                .fillMaxSize()
                .background(Color(0xFF191A21))
                .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RailButton(
            icon = Icons.Filled.Close,
            description = "Close application",
            selected = false,
            onClick = onCloseApplication,
        )
        HorizontalDivider(color = Color(0x33444A65))
        windows.forEach { window ->
            RailButton(
                icon = window.railIcon(),
                description = "Toggle ${window.title}",
                selected = window.visible,
                onClick = { onToggleWindow(window.id) },
            )
        }
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ActionIconButton(
        icon = icon,
        description = description,
        onClick = onClick,
        modifier =
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) Color(0xFF44475A) else Color(0xFF282A36))
                .border(1.dp, Color(0x33444A65), RoundedCornerShape(16.dp)),
    )
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

@Composable
internal fun ComposeWorkspaceHeader(
    config: AppConfig,
    beanDefinitionCount: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp),
    ) {
        Text(
            text = "Visual Agent",
            color = Color(0xFFF8F8F2),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text =
                "Compose Multiplatform · Provider ${config.normalizedProvider()} · " +
                    "Model ${config.activeModel()} · Beans $beanDefinitionCount",
            color = Color(0xFFBD93F9),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun PanelStatus(status: String) {
    Text(status, color = Color(0xFF8BE9FD), style = MaterialTheme.typography.bodySmall)
}
