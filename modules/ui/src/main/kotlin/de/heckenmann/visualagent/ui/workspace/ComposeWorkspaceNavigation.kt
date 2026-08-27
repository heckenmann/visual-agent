@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

/**
 * Left-hand rail that toggles panels, reorders them and adjusts their widths.
 *
 * Panel buttons are displayed in a vertical, animated drag-and-drop list. Each button shows a
 * ridged drag handle on its right side; dragging only that handle reorders the panel. Horizontal
 * drags on the button body adjust the panel's preferred width in 20 px steps. The application
 * close button lives at the bottom of the rail and is not part of the reorderable list.
 *
 * @param windows All workspace panels in persistent order
 * @param onToggleWindow Callback that toggles a panel's visibility
 * @param onReorderWindows Callback that receives the panels in their new order after a drag
 *   gesture settles
 * @param onPanelWidthChanged Callback that receives a new preferred width for a panel
 * @param showPanelLabels Whether panel names are shown beside their icons
 * @param onTogglePanelLabels Callback that toggles the panel-name display mode
 * @param onCloseApplication Callback that closes the application
 * @param modalRequester Modal requester used to show the panel width slider dialog
 *
 * Use cases: UC-0000034, UC-0000035, UC-0000036, UC-0000070.
 */
@Composable
internal fun ComposeRail(
    windows: List<ComposeWorkspaceWindow>,
    onToggleWindow: (String) -> Unit,
    onReorderWindows: (List<ComposeWorkspaceWindow>) -> Unit,
    onPanelWidthChanged: (String, Int) -> Unit,
    showPanelLabels: Boolean = true,
    onTogglePanelLabels: () -> Unit = {},
    onCloseApplication: () -> Unit,
    modalRequester: ComposeModalRequester,
) {
    val railWidth = navigationRailWidth(windows, showPanelLabels)
    val animatedRailWidth by animateDpAsState(targetValue = railWidth, label = "navigation rail width")
    var previousWindowIds by remember { mutableStateOf(windows.map(ComposeWorkspaceWindow::id)) }
    var reorderOffsets by remember { mutableStateOf(emptyMap<String, Int>()) }
    var settledWindowIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(windows) {
        val windowIds = windows.map(ComposeWorkspaceWindow::id)
        reorderOffsets = railReorderOffsets(previousWindowIds, windowIds, settledWindowIds)
        if (settledWindowIds == windowIds) {
            settledWindowIds = null
        }
        previousWindowIds = windowIds
    }
    Column(
        modifier =
            Modifier
                .width(animatedRailWidth)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReorderableColumn(
            list = windows,
            onSettle = { fromIndex, toIndex ->
                val reordered = windows.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                settledWindowIds = reordered.map(ComposeWorkspaceWindow::id)
                onReorderWindows(reordered)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().animateContentSize(),
        ) { _, window, isDragging ->
            key(window.id) {
                ReorderableItem {
                    DraggableRailButton(
                        window = window,
                        reorderOffsetPx = reorderOffsets[window.id] ?: 0,
                        showLabel = showPanelLabels,
                        selected = window.visible,
                        isDragging = isDragging,
                        onToggle = { onToggleWindow(window.id) },
                        onWidthChange = { width -> onPanelWidthChanged(window.id, width) },
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
        }
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDividerLine()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StaticRailButton(
                icon = Icons.Filled.TextFields,
                description = if (showPanelLabels) "Hide panel labels" else "Show panel labels",
                selected = showPanelLabels,
                onClick = onTogglePanelLabels,
                modifier = Modifier.fillMaxWidth(),
            )
            StaticRailButton(
                icon = Icons.Filled.Close,
                description = "Close application",
                selected = false,
                onClick = onCloseApplication,
                modifier = Modifier.fillMaxWidth(),
                alert = true,
            )
        }
    }
}

private const val RAIL_ITEM_STRIDE_PX = 46

/**
 * Calculates visual position offsets for an externally reordered rail list.
 *
 * A reordered list returned by [ReorderableColumn] has already been animated while the user was
 * dragging it, so that specific state update must not start a second animation.
 */
internal fun railReorderOffsets(
    previousWindowIds: List<String>,
    windowIds: List<String>,
    settledWindowIds: List<String>?,
): Map<String, Int> {
    if (settledWindowIds == windowIds) return emptyMap()
    val previousIndexes = previousWindowIds.withIndex().associate { (index, id) -> id to index }
    return windowIds
        .mapIndexed { index, id -> id to ((previousIndexes[id] ?: index) - index) * RAIL_ITEM_STRIDE_PX }
        .toMap()
}

@Composable
private fun navigationRailWidth(
    windows: List<ComposeWorkspaceWindow>,
    showPanelLabels: Boolean,
): Dp {
    if (!showPanelLabels) return 60.dp
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val widestLabel =
        windows
            .map { window -> textMeasurer.measure(AnnotatedString(window.title), style = labelStyle).size.width }
            .maxOrNull() ?: 0
    val fixedContentWidth =
        18.dp +
            ButtonDefaults.IconSpacing +
            10.dp +
            ButtonDefaults.ContentPadding.calculateLeftPadding(LayoutDirection.Ltr) +
            ButtonDefaults.ContentPadding.calculateRightPadding(LayoutDirection.Ltr) +
            24.dp
    return with(density) { (widestLabel.toDp() + fixedContentWidth).coerceAtLeast(60.dp) }
}
