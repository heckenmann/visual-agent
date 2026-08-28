@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

/**
 * Renders the visible workspace panels in a single horizontally scrollable row.
 *
 * Every visible panel gets a column that spans the full row height. Every panel,
 * including the rightmost one, has a draggable resizer handle on its right edge
 * so users can resize each panel independently. The panel order can be changed
 * by dragging the panel header grip; reordering from the left rail or from the
 * panels themselves animates the row using `sh.calvin.reorderable` lazy item
 * animations.
 *
 * @param windows All workspace panels in persistent order
 * @param panelServices Services required by the individual panel bodies
 * @param onToggleWindow Callback that toggles the visibility of a panel
 * @param onReorderWindows Callback that receives all panels in their new order after a drag gesture
 * @param onResizeWindow Callback that receives an updated preferred width for a panel
 * @param minPanelWidth Minimum width for each panel in pixels
 * @param viewport Available workspace dimensions used by resizer math
 * @param modifier Modifier applied to the workspace root
 *
 * Use cases: UC-0000034, UC-0000035, UC-0000036.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ComposeSplitWorkspace(
    windows: List<ComposeWorkspaceWindow>,
    panelServices: ComposePanelServices,
    onToggleWindow: (String) -> Unit,
    onReorderWindows: (List<ComposeWorkspaceWindow>) -> Unit,
    onResizeWindow: (String, Int) -> Unit,
    minPanelWidth: Int,
    viewport: ComposeWorkspaceViewport,
    modifier: Modifier = Modifier,
) {
    val visibleWindows = windows.filter { it.visible }
    val windowsState = rememberUpdatedState(windows)
    val resizeUpdatedState = rememberUpdatedState(onResizeWindow)
    val horizontalScrollState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = horizontalScrollState,
            onMove = { from, to ->
                val reordered =
                    windowsState.value.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                onReorderWindows(reordered)
            },
        )
    Box(modifier = modifier.fillMaxSize()) {
        WorkspaceBackdrop()
        if (windows.isEmpty()) {
            EmptyWorkspace()
        } else {
            val widths = rowPanelWidths(visibleWindows)
            val widthsById = visibleWindows.mapIndexed { index, window -> window.id to widths[index] }.toMap()
            val rowWidthPx =
                widths.sum() +
                    (visibleWindows.size * WORKSPACE_PANEL_RESIZER_WIDTH) +
                    ((visibleWindows.size - 1) * WORKSPACE_PANEL_GAP)
            val canScroll = rowWidthPx > viewport.width
            val panelHeight =
                (viewport.height - (2 * WORKSPACE_PANEL_GAP)).coerceAtLeast(0)
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .onPointerEvent(PointerEventType.Scroll) { event ->
                                    val change = event.changes.firstOrNull()
                                    val scrollDelta = change?.scrollDelta
                                    val horizontalScrollDelta = scrollDelta?.x ?: 0f
                                    if (horizontalScrollDelta != 0f && canScroll) {
                                        scrollScope.launch {
                                            horizontalScrollState.scrollBy(
                                                horizontalScrollDelta * HORIZONTAL_WHEEL_SCROLL_STEP,
                                            )
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                },
                    ) {
                        LazyRow(
                            state = horizontalScrollState,
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            items(
                                items = windows,
                                key = { it.id },
                            ) { window ->
                                SplitPanelItem(
                                    state = reorderableState,
                                    window = window,
                                    visible = window.visible,
                                    panelServices = panelServices,
                                    width = widthsById[window.id] ?: window.preferredWidth.coerceAtLeast(minPanelWidth),
                                    isLast = window.id == visibleWindows.lastOrNull()?.id,
                                    onWidthChanged = { next -> resizeUpdatedState.value.invoke(window.id, next) },
                                    onCloseWindow = { onToggleWindow(window.id) },
                                    minPanelWidth = minPanelWidth,
                                    rowHeight = panelHeight,
                                )
                            }
                        }
                    }
                    if (canScroll) {
                        ScrollArrow(
                            direction = -1,
                            scrollState = horizontalScrollState,
                            isClosing = { panelServices.lifecycle.closing },
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                        )
                        ScrollArrow(
                            direction = 1,
                            scrollState = horizontalScrollState,
                            isClosing = { panelServices.lifecycle.closing },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = canScroll,
                    enter = fadeIn(animationSpec = workspacePanelAnimationSpec()),
                    exit = fadeOut(animationSpec = workspacePanelAnimationSpec()),
                    label = "workspace scrollbar visibility",
                ) {
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(horizontalScrollState),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        style =
                            ScrollbarStyle(
                                minimalHeight = 16.dp,
                                thickness = 8.dp,
                                shape = RoundedCornerShape(4.dp),
                                hoverDurationMillis = 300,
                                hoverColor = MaterialTheme.colorScheme.primary,
                                unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0x33 / 255f),
                            ),
                    )
                }
            }
            AnimatedVisibility(
                visible = visibleWindows.isEmpty(),
                enter = fadeIn(animationSpec = workspacePanelAnimationSpec()),
                exit = fadeOut(animationSpec = workspacePanelAnimationSpec()),
                label = "empty workspace visibility",
            ) {
                EmptyWorkspace()
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
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0x55 / 255f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0x1A / 255f), shape),
    )
}

@Composable
private fun EmptyWorkspace() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No panels are open. Use the rail to choose a workspace panel.",
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LazyItemScope.SplitPanelItem(
    state: ReorderableLazyListState,
    window: ComposeWorkspaceWindow,
    visible: Boolean,
    panelServices: ComposePanelServices,
    width: Int,
    isLast: Boolean,
    onWidthChanged: (Int) -> Unit,
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
    val animatedWidthPx = animatedWidth.value.roundToInt()
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
                    width = animatedWidthPx,
                    onCloseWindow = onCloseWindow,
                    minPanelWidth = minPanelWidth,
                    modifier = Modifier.height(animatedHeight),
                )
                PanelResizer(
                    currentWidth = width,
                    onWidthChanged = onWidthChanged,
                    minPanelWidth = minPanelWidth,
                )
                if (!isLast) {
                    Spacer(modifier = Modifier.width(WORKSPACE_PANEL_GAP.dp))
                }
            }
        }
    }
}

private const val HORIZONTAL_WHEEL_SCROLL_STEP = 50f
