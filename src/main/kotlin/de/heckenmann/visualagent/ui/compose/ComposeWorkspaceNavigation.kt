@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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

/**
 * Left-hand rail that toggles panels, reorders them and adjusts their widths.
 *
 * Vertical drags on a panel button reorder the panel. Horizontal drags adjust
 * the panel's preferred width in 20 px steps. The application close button lives
 * at the bottom of the rail.
 *
 * @param windows All workspace panels in persistent order
 * @param onToggleWindow Callback that toggles a panel's visibility
 * @param onMoveWindowEarlier Callback that moves a panel one slot up
 * @param onMoveWindowLater Callback that moves a panel one slot down
 * @param onPanelWidthChanged Callback that receives a new preferred width for a panel
 * @param onCloseApplication Callback that closes the application
 * @param modalRequester Modal requester used to show the panel width slider dialog
 */
@Composable
internal fun ComposeRail(
    windows: List<ComposeWorkspaceWindow>,
    onToggleWindow: (String) -> Unit,
    onMoveWindowEarlier: (String) -> Unit,
    onMoveWindowLater: (String) -> Unit,
    onPanelWidthChanged: (String, Int) -> Unit,
    onCloseApplication: () -> Unit,
    modalRequester: ComposeModalRequester,
) {
    Column(
        modifier =
            Modifier
                .width(60.dp)
                .fillMaxSize()
                .background(Color(0xFF181923))
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            windows.forEach { window ->
                RailButton(
                    icon = window.railIcon(),
                    description = "Toggle ${window.title}",
                    selected = window.visible,
                    currentWidth = window.preferredWidth,
                    onMoveEarlier = { onMoveWindowEarlier(window.id) },
                    onMoveLater = { onMoveWindowLater(window.id) },
                    onWidthChange = { width -> onPanelWidthChanged(window.id, width) },
                    onClick = { onToggleWindow(window.id) },
                    onRequestWidthDialog = {
                        modalRequester.request(
                            ComposeContentModal(
                                title = "Width: ${window.title}",
                                content = { dismiss ->
                                    PanelWidthSlider(
                                        current = window.preferredWidth,
                                        onWidthChange = { onPanelWidthChanged(window.id, it) },
                                        onDismiss = dismiss,
                                    )
                                },
                            ),
                        )
                    },
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = Color(0x33444A65), modifier = Modifier.padding(vertical = 10.dp))
        RailButton(
            icon = Icons.Filled.Close,
            description = "Close application",
            selected = false,
            currentWidth = MIN_PANEL_WIDTH,
            onClick = onCloseApplication,
        )
    }
}

@Composable
private fun RailButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    currentWidth: Int,
    onMoveEarlier: (() -> Unit)? = null,
    onMoveLater: (() -> Unit)? = null,
    onWidthChange: ((Int) -> Unit)? = null,
    onClick: () -> Unit,
    onRequestWidthDialog: (() -> Unit)? = null,
) {
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        ActionIconButton(
            icon = icon,
            description = description,
            onClick = onClick,
            onLongClick =
                if (onRequestWidthDialog != null) {
                    { menuExpanded = true }
                } else {
                    null
                },
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color(0xFF333644) else Color(0xFF23252F))
                    .border(1.dp, if (selected) Color(0xCC50FA7B) else Color(0x2AFFFFFF), RoundedCornerShape(8.dp))
                    .pointerInput(onMoveEarlier, onMoveLater, onWidthChange) {
                        detectDragGestures(
                            onDragEnd = {
                                verticalOffset = 0f
                                horizontalOffset = 0f
                            },
                            onDragCancel = {
                                verticalOffset = 0f
                                horizontalOffset = 0f
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            verticalOffset += dragAmount.y
                            horizontalOffset += dragAmount.x
                            if (kotlin.math.abs(verticalOffset) >= RAIL_REORDER_THRESHOLD_PX) {
                                if (verticalOffset < 0 && onMoveEarlier != null) {
                                    onMoveEarlier()
                                } else if (verticalOffset > 0 && onMoveLater != null) {
                                    onMoveLater()
                                }
                                verticalOffset = 0f
                                horizontalOffset = 0f
                            }
                            if (onWidthChange != null && kotlin.math.abs(horizontalOffset) >= RAIL_WIDTH_STEP_PX) {
                                val steps = (horizontalOffset / RAIL_WIDTH_STEP_PX).toInt()
                                val next =
                                    (currentWidth + steps * RAIL_WIDTH_STEP_PX.toInt())
                                        .coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH)
                                onWidthChange(next)
                                horizontalOffset -= steps * RAIL_WIDTH_STEP_PX
                            }
                        }
                    },
            iconSize = 18.dp,
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(Color(0xFF23252F)),
        ) {
            DropdownMenuItem(
                text = { Text("Set width…", color = Color(0xFFF8F8F2)) },
                onClick = {
                    menuExpanded = false
                    onRequestWidthDialog?.invoke()
                },
            )
        }
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
internal fun ComposeWorkspaceHeader(
    providerName: String,
    modelName: String,
    beanDefinitionCount: Int,
    inFlight: InFlightState,
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
        InFlightIndicator(state = inFlight)
    }
}

private const val RAIL_REORDER_THRESHOLD_PX = 34f
private const val RAIL_WIDTH_STEP_PX = 20f
internal const val MIN_PANEL_WIDTH = 200
internal const val MAX_PANEL_WIDTH = 800

@Composable
internal fun PanelStatus(status: String) {
    Text(status, color = Color(0xFF8BE9FD), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PanelWidthSlider(
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
