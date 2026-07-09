@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
    val backgroundColor = if (selected) Color(0xFF333644) else Color(0xFF23252F)
    val borderColor = if (selected) Color(0xCC50FA7B) else Color(0x2AFFFFFF)
    ReorderableItem {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .draggableHandle()
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
                    }.background(backgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .alpha(if (isDragging) 0.85f else 1f)
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
                tint = if (selected) Color(0xFF8BE9FD) else LocalContentColor.current,
            )
        }
    }
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        modifier = Modifier.background(Color(0xFF23252F)),
    ) {
        DropdownMenuItem(
            text = { Text("Set width…", color = Color(0xFFF8F8F2)) },
            onClick = {
                menuExpanded = false
                onRequestWidthDialog()
            },
        )
    }
}

@Composable
internal fun StaticRailButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) Color(0xFF333644) else Color(0xFF23252F)
    val borderColor = if (selected) Color(0xCC50FA7B) else Color(0x2AFFFFFF)
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
            tint = if (selected) Color(0xFF8BE9FD) else LocalContentColor.current,
        )
    }
}
