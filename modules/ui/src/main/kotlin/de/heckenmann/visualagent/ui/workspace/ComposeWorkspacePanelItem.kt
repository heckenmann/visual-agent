package de.heckenmann.visualagent.ui.workspace

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.application.ComposePanelServices
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import kotlin.math.roundToInt

/** Renders the decorative background behind the workspace panel row. */
@Composable
internal fun workspaceBackdrop() {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0x55 / 255f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0x1A / 255f), shape),
    )
}

/** Renders the empty state shown when no workspace panel is available. */
@Composable
internal fun emptyWorkspace() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No panels are open. Use the rail to choose a workspace panel.",
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Renders one reorderable workspace panel and its direct-manipulation resizer. */
@Composable
internal fun LazyItemScope.splitPanelItem(
    state: ReorderableLazyListState,
    window: ComposeWorkspaceWindow,
    visible: Boolean,
    panelServices: ComposePanelServices,
    width: Int,
    isResizing: Boolean,
    isLast: Boolean,
    onPreviewWidthChanged: (Int) -> Unit,
    onWidthCommitted: (Int) -> Unit,
    onResizeCancelled: () -> Unit,
    onCloseWindow: () -> Unit,
    minPanelWidth: Int,
    rowHeight: Int,
) {
    val animatedWidth by
        animateDpAsState(
            targetValue = width.coerceAtLeast(minPanelWidth).dp,
            animationSpec = workspacePanelAnimationSpec(),
            label = "workspace panel width",
        )
    val animatedHeight by
        animateDpAsState(
            targetValue = rowHeight.dp,
            animationSpec = workspacePanelAnimationSpec(),
            label = "workspace panel height",
        )
    val renderedWidth = if (isResizing) width.coerceAtLeast(minPanelWidth).dp else animatedWidth
    val renderedWidthUnits = renderedWidth.value.roundToInt()
    ReorderableItem(
        state = state,
        key = window.id,
        modifier = Modifier.testTag("workspace-panel-${window.id}"),
    ) { isDragging ->
        WorkspacePanelVisibility(visible = visible) {
            Row(
                modifier =
                    Modifier
                        .padding(vertical = WORKSPACE_PANEL_GAP.dp)
                        .height(animatedHeight),
            ) {
                SplitPanelContent(
                    window = window,
                    panelServices = panelServices,
                    isDragging = isDragging,
                    width = renderedWidthUnits,
                    onCloseWindow = onCloseWindow,
                    minPanelWidth = minPanelWidth,
                    modifier =
                        Modifier
                            .height(animatedHeight)
                            .testTag("workspace-panel-content-${window.id}"),
                )
                panelResizer(
                    currentWidth = width,
                    onPreviewWidthChanged = onPreviewWidthChanged,
                    onWidthCommitted = onWidthCommitted,
                    onCancelled = onResizeCancelled,
                    minPanelWidth = minPanelWidth,
                    modifier = Modifier.testTag("workspace-panel-resizer-${window.id}"),
                )
                if (!isLast) {
                    Spacer(modifier = Modifier.width(WORKSPACE_PANEL_GAP.dp))
                }
            }
        }
    }
}
