package de.heckenmann.visualagent.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Renders a direct-manipulation workspace-panel width resizer. */
@Composable
internal fun panelResizer(
    currentWidth: Int,
    onPreviewWidthChanged: (Int) -> Unit,
    onWidthCommitted: (Int) -> Unit,
    onCancelled: () -> Unit,
    minPanelWidth: Int,
) {
    val currentWidthState = rememberUpdatedState(currentWidth)
    val onPreviewWidthChangedState = rememberUpdatedState(onPreviewWidthChanged)
    val onWidthCommittedState = rememberUpdatedState(onWidthCommitted)
    val onCancelledState = rememberUpdatedState(onCancelled)
    val minPanelWidthState = rememberUpdatedState(minPanelWidth)
    val density = LocalDensity.current
    val dragStartWidth = remember { mutableStateOf<Int?>(null) }
    val dragOffsetDp = remember { mutableStateOf(0f) }
    val previewWidth = remember { mutableStateOf<Int?>(null) }
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(WORKSPACE_PANEL_RESIZER_WIDTH.dp)
                .semantics { contentDescription = "Resize panel" }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragStartWidth.value = currentWidthState.value
                            dragOffsetDp.value = 0f
                            previewWidth.value = currentWidthState.value
                            onPreviewWidthChangedState.value.invoke(currentWidthState.value)
                        },
                        onDragEnd = {
                            previewWidth.value?.let(onWidthCommittedState.value::invoke)
                            dragStartWidth.value = null
                            dragOffsetDp.value = 0f
                            previewWidth.value = null
                        },
                        onDragCancel = {
                            dragStartWidth.value = null
                            dragOffsetDp.value = 0f
                            previewWidth.value = null
                            onCancelledState.value.invoke()
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val startWidth = dragStartWidth.value ?: currentWidthState.value
                        dragOffsetDp.value += with(density) { dragAmount.x.toDp().value }
                        val next = resizePanelWidth(startWidth, dragOffsetDp.value.roundToInt(), minPanelWidthState.value, MAX_PANEL_WIDTH)
                        previewWidth.value = next
                        onPreviewWidthChangedState.value.invoke(next)
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
            resizerGrip()
        }
    }
}

@Composable
private fun resizerGrip() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
