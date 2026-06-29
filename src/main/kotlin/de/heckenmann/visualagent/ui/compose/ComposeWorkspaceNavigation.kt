@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeRail(
    windows: List<ComposeWorkspaceWindow>,
    onToggleWindow: (String) -> Unit,
    onCloseApplication: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(60.dp)
                .fillMaxSize()
                .background(Color(0xFF181923))
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RailButton(icon = Icons.Filled.Close, description = "Close application", selected = false, onClick = onCloseApplication)
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
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) Color(0xFF333644) else Color(0xFF23252F))
                .border(1.dp, if (selected) Color(0xCC50FA7B) else Color(0x2AFFFFFF), RoundedCornerShape(8.dp)),
        iconSize = 18.dp,
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
    providerName: String,
    modelName: String,
    beanDefinitionCount: Int,
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
    }
}

@Composable
internal fun PanelStatus(status: String) {
    Text(status, color = Color(0xFF8BE9FD), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun HeaderChip(
    label: String,
    value: String,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF242631))
                .border(1.dp, Color(0x33444A65), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$label $value",
            color = Color(0xFFBD93F9),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
