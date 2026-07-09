@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.compose.WORKSPACE_PANEL_RESIZER_WIDTH
import sh.calvin.reorderable.ReorderableCollectionItemScope

@Composable
internal fun ReorderableCollectionItemScope.SplitPanelContent(
    window: ComposeWorkspaceWindow,
    panelServices: ComposePanelServices,
    isDragging: Boolean,
    width: Int,
    onCloseWindow: () -> Unit,
    minPanelWidth: Int,
    modifier: Modifier,
) {
    val primary = window.id == "chat"
    val shape = RoundedCornerShape(8.dp)
    val borderColor =
        if (isDragging) {
            MaterialTheme.colorScheme.secondary
        } else if (primary) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0x99 / 255f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0x55 / 255f)
        }
    Card(
        modifier =
            modifier
                .width(width.coerceAtLeast(minPanelWidth).dp)
                .clip(shape)
                .border(
                    width = if (isDragging) 2.dp else 1.dp,
                    color = borderColor,
                    shape = shape,
                ),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (primary) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0xEE / 255f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0xE3 / 255f)
                    },
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    if (isDragging) {
                        12.dp
                    } else if (primary) {
                        2.dp
                    } else {
                        1.dp
                    },
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SplitPanelHeader(
                window = window,
                primary = primary,
                onClose = onCloseWindow,
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
private fun ReorderableCollectionItemScope.SplitPanelHeader(
    window: ComposeWorkspaceWindow,
    primary: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (primary) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHigh)
                .draggableHandle()
                .padding(horizontal = 10.dp, vertical = if (primary) 8.dp else 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (primary) 28.dp else 26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (primary) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0x14 / 255f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0x12 / 255f)
                        },
                    ).border(
                        1.dp,
                        if (primary) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0x40 / 255f)
                        } else {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0x20 / 255f)
                        },
                        RoundedCornerShape(7.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = window.railIcon(),
                contentDescription = null,
                tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(if (primary) 17.dp else 16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = window.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = window.subtitle,
                color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ActionIconButton(
            icon = Icons.Filled.Close,
            description = "Close ${window.title} panel",
            onClick = onClose,
            modifier = Modifier.size(if (primary) 28.dp else 26.dp),
            iconSize = if (primary) 18.dp else 16.dp,
        )
    }
}

@Composable
internal fun PanelResizer(
    currentWidth: Int,
    onWidthChanged: (Int) -> Unit,
    minPanelWidth: Int,
) {
    val currentWidthState = rememberUpdatedState(currentWidth)
    val onWidthChangedState = rememberUpdatedState(onWidthChanged)
    val minPanelWidthState = rememberUpdatedState(minPanelWidth)
    val dragOffset = remember { mutableStateOf(0f) }
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(WORKSPACE_PANEL_RESIZER_WIDTH.dp)
                .semantics { contentDescription = "Resize panel" }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { dragOffset.value = 0f },
                        onDragCancel = { dragOffset.value = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffset.value += dragAmount.x
                        val threshold = WORKSPACE_RESIZER_THRESHOLD_PX
                        val steps = (dragOffset.value / threshold).toInt()
                        if (steps != 0) {
                            val next =
                                resizePanelWidth(
                                    currentWidthState.value,
                                    steps * threshold.toInt(),
                                    minPanelWidthState.value,
                                    MAX_PANEL_WIDTH,
                                )
                            if (next != currentWidthState.value) {
                                onWidthChangedState.value.invoke(next)
                            }
                            dragOffset.value -= steps * threshold
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(8.dp)
                    .fillMaxHeight(0.4f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0x55 / 255f)),
            contentAlignment = Alignment.Center,
        ) {
            ResizerGrip()
        }
    }
}

@Composable
private fun ResizerGrip() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .padding(vertical = 2.dp)
                        .size(width = 6.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0xAA / 255f)),
            )
        }
    }
}

private const val WORKSPACE_RESIZER_THRESHOLD_PX = 10f
