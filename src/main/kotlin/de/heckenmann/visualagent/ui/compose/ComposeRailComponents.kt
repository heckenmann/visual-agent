@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableColumnScope
import sh.calvin.reorderable.ReorderableItem
import kotlin.math.abs

private const val RAIL_WIDTH_STEP_PX = 20f

@Composable
internal fun ReorderableColumnScope.DraggableRailButton(
    window: ComposeWorkspaceWindow,
    selected: Boolean,
    isDragging: Boolean,
    onToggle: () -> Unit,
    onWidthChange: (Int) -> Unit,
    onRequestWidthDialog: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    val backgroundColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    val borderColor = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0x2A / 255f)
    ReorderableItem {
        Row(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .alpha(if (isDragging) 0.85f else 1f)
                    .pointerInput(window.id, onWidthChange) {
                        detectDragGestures(
                            onDragEnd = { horizontalOffset = 0f },
                            onDragCancel = { horizontalOffset = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            horizontalOffset += dragAmount.x
                            if (abs(horizontalOffset) >= RAIL_WIDTH_STEP_PX) {
                                val steps = (horizontalOffset / RAIL_WIDTH_STEP_PX).toInt()
                                val next =
                                    (window.preferredWidth + steps * RAIL_WIDTH_STEP_PX.toInt())
                                        .coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH)
                                onWidthChange(next)
                                horizontalOffset -= steps * RAIL_WIDTH_STEP_PX
                            }
                        }
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            role = Role.Button,
                            onClick = onToggle,
                            onLongClick = { menuExpanded = true },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = window.railIcon(),
                    contentDescription = "Toggle ${window.title}",
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
                )
            }
            RailDragHandle(
                window = window,
                modifier = Modifier.draggableHandle(),
            )
        }
    }
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        DropdownMenuItem(
            text = { Text("Set width…", color = MaterialTheme.colorScheme.onSurface) },
            onClick = {
                menuExpanded = false
                onRequestWidthDialog()
            },
        )
    }
}

@Composable
private fun RailDragHandle(
    window: ComposeWorkspaceWindow,
    modifier: Modifier = Modifier,
) {
    val gripColor = MaterialTheme.colorScheme.tertiary
    Box(
        modifier =
            modifier
                .width(10.dp)
                .fillMaxHeight()
                .padding(vertical = 6.dp, horizontal = 2.dp)
                .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Drag ${window.title}" },
        ) {
            repeat(4) {
                Box(
                    modifier =
                        Modifier
                            .width(6.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(gripColor),
                )
            }
        }
    }
}

@Composable
internal fun StaticRailButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    val borderColor = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0x2A / 255f)
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = if (selected) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
        )
    }
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
internal fun HorizontalDividerLine() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))
}

internal const val MIN_PANEL_WIDTH = 200
internal const val MAX_PANEL_WIDTH = 2400
