@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ComposeSplitWorkspace(
    windows: List<ComposeWorkspaceWindow>,
    panelServices: ComposePanelServices,
    onToggleWindow: (String) -> Unit,
    onMoveWindowEarlier: (String) -> Unit,
    onMoveWindowLater: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleWindows = windows.filter { it.visible }
    BoxWithConstraints(modifier = modifier) {
        val viewport =
            ComposeWorkspaceViewport(
                width = maxWidth.value.roundToInt().coerceAtLeast(1),
                height = maxHeight.value.roundToInt().coerceAtLeast(1),
            )
        val boundsByPanel = splitWorkspaceBounds(windows, viewport)
        val primaryPanelId = boundsByPanel.maxByOrNull { (_, bounds) -> bounds.width * bounds.height }?.key
        Box(Modifier.fillMaxSize()) {
            WorkspaceBackdrop()
            if (visibleWindows.isEmpty()) {
                EmptyWorkspace()
            } else {
                visibleWindows.forEach { window ->
                    val bounds = boundsByPanel.getValue(window.id)
                    SplitPanel(
                        window = window,
                        panelServices = panelServices,
                        primary = primaryPanelId == window.id,
                        canMoveEarlier = windows.indexOfFirst { it.id == window.id } > 0,
                        canMoveLater = windows.indexOfFirst { it.id == window.id } < windows.lastIndex,
                        onMoveEarlier = { onMoveWindowEarlier(window.id) },
                        onMoveLater = { onMoveWindowLater(window.id) },
                        onHide = { onToggleWindow(window.id) },
                        modifier =
                            Modifier
                                .offset(x = bounds.x.dp, y = bounds.y.dp)
                                .size(bounds.width.dp, bounds.height.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceBackdrop() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0x55191A21))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(28.dp)),
    )
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
    primary: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(if (primary) 28.dp else 22.dp)
    Card(
        modifier =
            modifier
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = if (primary) Color(0x9950FA7B) else Color(0x5544475A),
                    shape = shape,
                ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (primary) Color(0xF0282A36) else Color(0xE6242631)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (primary) 10.dp else 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SplitPanelHeader(
                window = window,
                primary = primary,
                canMoveEarlier = canMoveEarlier,
                canMoveLater = canMoveLater,
                onMoveEarlier = onMoveEarlier,
                onMoveLater = onMoveLater,
                onHide = onHide,
            )
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
private fun SplitPanelHeader(
    window: ComposeWorkspaceWindow,
    primary: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (primary) Color(0xFF343746) else Color(0xFF2C2F3B))
                .padding(horizontal = 16.dp, vertical = if (primary) 14.dp else 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (primary) 34.dp else 30.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (primary) Color(0x334FFFA1) else Color(0x263BD8FF))
                    .border(1.dp, if (primary) Color(0x7750FA7B) else Color(0x448BE9FD), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = window.railIcon(),
                contentDescription = null,
                tint = if (primary) Color(0xFF50FA7B) else Color(0xFF8BE9FD),
                modifier = Modifier.size(if (primary) 20.dp else 18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = window.title,
                color = Color(0xFFF8F8F2),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = window.subtitle,
                color = if (primary) Color(0xFF8BE9FD) else Color(0xFFBFBBD0),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderActionButton(
                icon = Icons.Filled.KeyboardArrowUp,
                description = "Move ${window.title} earlier",
                onClick = onMoveEarlier,
                enabled = canMoveEarlier,
            )
            HeaderActionButton(
                icon = Icons.Filled.KeyboardArrowDown,
                description = "Move ${window.title} later",
                onClick = onMoveLater,
                enabled = canMoveLater,
            )
            HeaderActionButton(
                icon = Icons.Filled.Close,
                description = "Hide ${window.title}",
                onClick = onHide,
                enabled = true,
            )
        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    ActionIconButton(
        icon = icon,
        description = description,
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x1AFFFFFF)),
    )
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
