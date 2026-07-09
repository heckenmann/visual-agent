@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableColumn

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
        ReorderableColumn(
            list = windows,
            onSettle = { fromIndex, toIndex ->
                val reordered = windows.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                onReorderWindows(reordered)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.animateContentSize(),
        ) { _, window, isDragging ->
            DraggableRailButton(
                window = window,
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
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDividerLine()
        StaticRailButton(
            icon = Icons.Filled.Close,
            description = "Close application",
            selected = false,
            onClick = onCloseApplication,
        )
    }
}
