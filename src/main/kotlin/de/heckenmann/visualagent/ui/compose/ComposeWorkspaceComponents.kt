@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cheonjaeung.compose.grid.BoxGrid
import com.cheonjaeung.compose.grid.BoxGridItemSpan
import com.cheonjaeung.compose.grid.SimpleGridCells
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
                BoxGrid(
                    rows = SimpleGridCells.Fixed(viewport.height),
                    columns = SimpleGridCells.Fixed(viewport.width),
                    modifier = Modifier.fillMaxSize(),
                ) {
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
                                    .position(row = bounds.y, column = bounds.x)
                                    .span {
                                        BoxGridItemSpan(
                                            row = bounds.height,
                                            column = bounds.width,
                                        )
                                    },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceBackdrop() {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0x55191A21))
                .border(1.dp, Color(0x1AFFFFFF), shape),
    )
}

@Composable
private fun EmptyWorkspace() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No panels are open. Use the rail to choose a workspace panel.",
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
    val shape = RoundedCornerShape(8.dp)
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
        colors = CardDefaults.cardColors(containerColor = if (primary) Color(0xEE252734) else Color(0xE321232D)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (primary) 2.dp else 1.dp),
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
                        .padding(10.dp),
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
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (primary) Color(0xFF2A2D39) else Color(0xFF262832))
                .pointerInput(canMoveEarlier, canMoveLater) {
                    detectDragGestures(
                        onDragEnd = { dragOffset = 0f },
                        onDragCancel = { dragOffset = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        val dominantDelta =
                            if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                                dragAmount.x
                            } else {
                                dragAmount.y
                            }
                        dragOffset += dominantDelta
                        when {
                            dragOffset <= -PANEL_REORDER_THRESHOLD_PX && canMoveEarlier -> {
                                onMoveEarlier()
                                dragOffset = 0f
                            }
                            dragOffset >= PANEL_REORDER_THRESHOLD_PX && canMoveLater -> {
                                onMoveLater()
                                dragOffset = 0f
                            }
                        }
                    }
                }.padding(horizontal = 10.dp, vertical = if (primary) 8.dp else 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (primary) 28.dp else 26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (primary) Color(0x244FFFA1) else Color(0x1F3BD8FF))
                    .border(1.dp, if (primary) Color(0x6650FA7B) else Color(0x338BE9FD), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = window.railIcon(),
                contentDescription = null,
                tint = if (primary) Color(0xFF50FA7B) else Color(0xFF8BE9FD),
                modifier = Modifier.size(if (primary) 17.dp else 16.dp),
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

private const val PANEL_REORDER_THRESHOLD_PX = 48f

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
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x14FFFFFF)),
        iconSize = 15.dp,
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
        "agents" ->
            SubAgentsPanel(
                agentManager = panelServices.agentManager,
                agentToolConfigService = panelServices.agentToolConfigService,
                toolRegistry = panelServices.toolRegistry,
                providerCatalogService = panelServices.providerCatalogService,
                modalRequester = panelServices.modalRequester,
            )
        "settings" ->
            SettingsPanel(
                config = panelServices.config,
                llmProvider = panelServices.llmProvider,
                providerCatalogService = panelServices.providerCatalogService,
                modalRequester = panelServices.modalRequester,
                onSettingsChanged = panelServices.onSettingsChanged,
            )
        "canvas" -> CanvasPanel(panelServices.canvasOperations, panelServices.workspaceFileService, panelServices.modalRequester)
    }
}
