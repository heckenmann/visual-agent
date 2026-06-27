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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

private fun ComposeWorkspaceWindow.railIcon(): ImageVector =
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
internal fun ComposeSplitWorkspace(
    windows: List<ComposeWorkspaceWindow>,
    panelServices: ComposePanelServices,
    modifier: Modifier = Modifier,
) {
    val visibleWindows = windows.filter { it.visible }
    Box(modifier = modifier) {
        when (visibleWindows.size) {
            0 -> EmptyWorkspace()
            1 -> SplitPanel(visibleWindows.single(), panelServices, Modifier.fillMaxSize())
            2 ->
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    visibleWindows.forEach { window ->
                        SplitPanel(window, panelServices, Modifier.weight(1f).fillMaxSize())
                    }
                }
            3 ->
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SplitPanel(visibleWindows[0], panelServices, Modifier.weight(1f).fillMaxSize())
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SplitPanel(visibleWindows[1], panelServices, Modifier.weight(1f).fillMaxWidth())
                        SplitPanel(visibleWindows[2], panelServices, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            else -> SplitGrid(visibleWindows, panelServices)
        }
    }
}

@Composable
private fun SplitGrid(
    windows: List<ComposeWorkspaceWindow>,
    panelServices: ComposePanelServices,
) {
    val leftColumn = windows.filterIndexed { index, _ -> index % 2 == 0 }
    val rightColumn = windows.filterIndexed { index, _ -> index % 2 == 1 }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SplitColumn(leftColumn, panelServices, Modifier.weight(1f).fillMaxSize())
        SplitColumn(rightColumn, panelServices, Modifier.weight(1f).fillMaxSize())
    }
}

@Composable
private fun SplitColumn(
    windows: List<ComposeWorkspaceWindow>,
    panelServices: ComposePanelServices,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        windows.forEach { window ->
            SplitPanel(window, panelServices, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun EmptyWorkspace() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No panels are open. Use the rail or press Cmd/Ctrl+1-6 to open a panel.",
            color = Color(0xFF8BE9FD),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SplitPanel(
    window: ComposeWorkspaceWindow,
    panelServices: ComposePanelServices,
    modifier: Modifier,
) {
    Card(
        modifier =
            modifier
                .border(1.dp, Color(0x6650FA7B), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE282A36)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SplitPanelHeader(window)
            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.25f))
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                WindowBody(window, panelServices)
            }
        }
    }
}

@Composable
private fun SplitPanelHeader(window: ComposeWorkspaceWindow) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF343746))
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = window.railIcon(), contentDescription = null, tint = Color(0xFF50FA7B), modifier = Modifier.size(20.dp))
            Text(text = window.title, color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
        }
        Text(text = window.subtitle, color = Color(0xFF8BE9FD), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WindowBody(
    window: ComposeWorkspaceWindow,
    panelServices: ComposePanelServices,
) {
    when (window.id) {
        "chat" -> ConversationPanel(panelServices.agentManager, panelServices.modalRequester)
        "todos" -> TodoPanel(panelServices.agentManager, panelServices.modalRequester)
        "files" -> FilesPanel(panelServices.workspaceFileService, panelServices.canvasOperations, panelServices.modalRequester)
        "agents" -> SubAgentsPanel(panelServices.agentManager, panelServices.modalRequester)
        "settings" -> SettingsPanel(panelServices.config)
        "canvas" -> CanvasPanel(panelServices.canvasOperations, panelServices.workspaceFileService, panelServices.modalRequester)
    }
}

@Composable
internal fun PanelStatus(status: String) {
    Text(status, color = Color(0xFF8BE9FD), style = MaterialTheme.typography.bodySmall)
}
