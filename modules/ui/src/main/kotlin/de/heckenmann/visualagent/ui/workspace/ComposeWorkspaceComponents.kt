@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import sh.calvin.reorderable.rememberReorderableLazyListState

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
 * @param minPanelWidth Minimum width for each panel in density-independent units
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
    var previewWidths by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var activeResizePanelId by remember { mutableStateOf<String?>(null) }
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
            val persistedWidths = rowPanelWidths(visibleWindows)
            val widthsById =
                visibleWindows
                    .mapIndexed { index, window ->
                        window.id to (
                            previewWidths[window.id]
                                ?: persistedWidths[index].coerceAtLeast(minPanelWidth)
                        )
                    }.toMap()
            val rowWidth = workspaceRowWidth(visibleWindows.map { widthsById.getValue(it.id) })
            val canScroll = rowWidth > viewport.width
            val activePreviewWidth = activeResizePanelId?.let(previewWidths::get)
            val panelHeight =
                (viewport.height - (2 * WORKSPACE_PANEL_GAP)).coerceAtLeast(0)
            LaunchedEffect(activeResizePanelId, activePreviewWidth, viewport.width) {
                val activePanelId = activeResizePanelId ?: return@LaunchedEffect
                withFrameNanos { }
                val layoutInfo = horizontalScrollState.layoutInfo
                val activeItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == activePanelId }
                val overflow =
                    activeItem?.let { item ->
                        (item.offset + item.size - layoutInfo.viewportEndOffset).coerceAtLeast(0)
                    } ?: 0
                if (overflow > 0) {
                    horizontalScrollState.scrollBy(overflow.toFloat())
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .testTag("workspace-viewport")
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
                            modifier = Modifier.fillMaxSize().testTag("workspace-horizontal-list"),
                            contentPadding = PaddingValues(start = WORKSPACE_PANEL_GAP.dp),
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
                                    isResizing = window.id in previewWidths,
                                    isLast = window.id == visibleWindows.lastOrNull()?.id,
                                    onPreviewWidthChanged = { next ->
                                        activeResizePanelId = window.id
                                        previewWidths = previewWidths + (window.id to next)
                                    },
                                    onWidthCommitted = { next ->
                                        previewWidths = previewWidths - window.id
                                        if (activeResizePanelId == window.id) {
                                            activeResizePanelId = null
                                        }
                                        resizeUpdatedState.value.invoke(window.id, next)
                                    },
                                    onResizeCancelled = {
                                        previewWidths = previewWidths - window.id
                                        if (activeResizePanelId == window.id) {
                                            activeResizePanelId = null
                                        }
                                    },
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
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("workspace-horizontal-scrollbar"),
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

private const val HORIZONTAL_WHEEL_SCROLL_STEP = 50f
