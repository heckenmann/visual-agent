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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableListItemScope

@Composable
internal fun ReorderableListItemScope.SplitPanelContent(
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
            Color(0xFFFF79C6)
        } else if (primary) {
            Color(0x9950FA7B)
        } else {
            Color(0x5544475A)
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
                containerColor = if (primary) Color(0xEE252734) else Color(0xE321232D),
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
private fun ReorderableListItemScope.SplitPanelHeader(
    window: ComposeWorkspaceWindow,
    primary: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (primary) Color(0xFF2A2D39) else Color(0xFF262832))
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
                .width(12.dp)
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
                    .width(2.dp)
                    .fillMaxHeight(0.4f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0x5544475A)),
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
                        .background(Color(0xAA8BE9FD)),
            )
        }
    }
}

private const val WORKSPACE_RESIZER_THRESHOLD_PX = 10f
