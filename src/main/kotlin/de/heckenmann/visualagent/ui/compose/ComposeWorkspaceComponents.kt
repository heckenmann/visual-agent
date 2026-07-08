@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableRow
import sh.calvin.reorderable.ReorderableRowScope

/**
 * Renders the visible workspace panels in a single horizontal row.
 *
 * Every visible panel gets a column that spans the full row height. Every panel,
 * including the rightmost one, has a draggable resizer handle on its right edge
 * so users can resize each panel independently. The panel order can be changed
 * by dragging the panel header grip thanks to `sh.calvin.reorderable`.
 *
 * @param windows All workspace panels in persistent order
 * @param panelServices Services required by the individual panel bodies
 * @param onToggleWindow Callback that toggles the visibility of a panel
 * @param onReorderWindows Callback that receives the visible panels in their
 *   new order after a drag gesture
 * @param onResizeWindow Callback that receives an updated list of panel widths
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
    val resizeUpdatedState = rememberUpdatedState(onResizeWindow)
    val horizontalScrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxSize()) {
        WorkspaceBackdrop()
        if (visibleWindows.isEmpty()) {
            EmptyWorkspace()
        } else {
            val widths = rowPanelWidths(visibleWindows)
            val rowWidthPx = widths.sum() + ((visibleWindows.size - 1) * WORKSPACE_PANEL_GAP)
            val canScroll = rowWidthPx > viewport.width
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState)
                                .onPointerEvent(PointerEventType.Scroll) { event ->
                                    val change = event.changes.firstOrNull()
                                    val scrollDelta = change?.scrollDelta
                                    val horizontalScrollDelta = scrollDelta?.x ?: 0f
                                    if (horizontalScrollDelta != 0f && canScroll) {
                                        val next =
                                            (horizontalScrollState.value + horizontalScrollDelta.toInt() * HORIZONTAL_WHEEL_SCROLL_STEP)
                                                .coerceIn(0, horizontalScrollState.maxValue)
                                        scrollScope.launch { horizontalScrollState.scrollTo(next) }
                                        event.changes.forEach { it.consume() }
                                    }
                                },
                    ) {
                        ReorderableRow(
                            list = visibleWindows,
                            onSettle = { fromIndex, toIndex ->
                                val reordered =
                                    visibleWindows.toMutableList().apply {
                                        add(toIndex, removeAt(fromIndex))
                                    }
                                onReorderWindows(reordered)
                            },
                            modifier = Modifier.fillMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(WORKSPACE_PANEL_GAP.dp),
                        ) { index, window, isDragging ->
                            SplitPanelItem(
                                window = window,
                                panelServices = panelServices,
                                isDragging = isDragging,
                                width = widths.getOrElse(index) { minPanelWidth },
                                onWidthChanged = { next -> resizeUpdatedState.value.invoke(window.id, next) },
                                onCloseWindow = { onToggleWindow(window.id) },
                                minPanelWidth = minPanelWidth,
                                rowHeight = viewport.height,
                            )
                        }
                    }
                    if (canScroll) {
                        ScrollArrow(
                            direction = -1,
                            scrollState = horizontalScrollState,
                            isClosing = { panelServices.lifecycle.closing },
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        ScrollArrow(
                            direction = 1,
                            scrollState = horizontalScrollState,
                            isClosing = { panelServices.lifecycle.closing },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
                if (canScroll) {
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(horizontalScrollState),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        style =
                            ScrollbarStyle(
                                minimalHeight = 16.dp,
                                thickness = 8.dp,
                                shape = RoundedCornerShape(4.dp),
                                hoverDurationMillis = 300,
                                hoverColor = Color(0xFF8BE9FD),
                                unhoverColor = Color(0x558BE9FD),
                            ),
                    )
                }
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
                .background(Color(0x55191A21))
                .border(1.dp, Color(0x1AFFFFFF), shape),
    )
}

@Composable
private fun EmptyWorkspace() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No panels are open. Use the rail to choose a workspace panel.",
            color = Color(0xFF8BE9FD),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReorderableRowScope.SplitPanelItem(
    window: ComposeWorkspaceWindow,
    panelServices: ComposePanelServices,
    isDragging: Boolean,
    width: Int,
    onWidthChanged: (Int) -> Unit,
    onCloseWindow: () -> Unit,
    minPanelWidth: Int,
    rowHeight: Int,
) {
    ReorderableItem(modifier = Modifier.height(rowHeight.dp)) {
        Row(modifier = Modifier.height(rowHeight.dp)) {
            SplitPanelContent(
                window = window,
                panelServices = panelServices,
                isDragging = isDragging,
                width = width,
                onCloseWindow = onCloseWindow,
                minPanelWidth = minPanelWidth,
                modifier = Modifier.height(rowHeight.dp),
            )
            PanelResizer(
                currentWidth = width,
                onWidthChanged = onWidthChanged,
                minPanelWidth = minPanelWidth,
            )
        }
    }
}

@Composable
internal fun WindowBody(
    window: ComposeWorkspaceWindow,
    panelServices: ComposePanelServices,
) {
    when (window.id) {
        "chat" ->
            ConversationPanel(
                agentManager = panelServices.agentManager,
                modalRequester = panelServices.modalRequester,
                inFlight = panelServices.inFlight,
                toolEventBus = panelServices.toolEventBus,
            )
        "todos" -> TodoPanel(panelServices.agentManager, panelServices.modalRequester)
        "files" -> FilesPanel(panelServices.workspaceFileService, panelServices.canvasOperations, panelServices.modalRequester)
        "agents" ->
            SubAgentsPanel(
                agentManager = panelServices.agentManager,
                agentToolConfigService = panelServices.agentToolConfigService,
                toolRegistry = panelServices.toolRegistry,
                providerCatalogService = panelServices.providerCatalogService,
                modalRequester = panelServices.modalRequester,
                inFlight = panelServices.inFlight,
            )
        "settings" ->
            SettingsPanel(
                config = panelServices.config,
                llmProvider = panelServices.llmProvider,
                providerCatalogService = panelServices.providerCatalogService,
                modalRequester = panelServices.modalRequester,
                onSettingsChanged = panelServices.onSettingsChanged,
                inFlight = panelServices.inFlight,
            )
        "canvas" -> CanvasPanel(panelServices.canvasOperations, panelServices.workspaceFileService, panelServices.modalRequester)
    }
}

/**
 * Scrolls the provided [scrollState] by [direction] * [SCROLL_ARROW_STEP_PX], clamped to the
 * scrollable range. The scroll is skipped when [isClosing] is true so that no coroutine is launched
 * while the application is shutting down.
 */
internal fun scrollArrowHandler(
    direction: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    scope: CoroutineScope,
    isClosing: () -> Boolean,
) {
    if (isClosing()) return
    val target = (scrollState.value + direction * SCROLL_ARROW_STEP_PX).coerceIn(0, scrollState.maxValue)
    scope.launch {
        if (isClosing()) return@launch
        scrollState.animateScrollTo(target)
    }
}

/**
 * Renders a directional arrow that scrolls the workspace row when clicked.
 *
 * Clicking the arrow animates the [scrollState] by a fixed step in the requested direction. The
 * scroll action is guarded by [isClosing] to avoid launching a coroutine after shutdown has started.
 *
 * @param direction Negative for left, positive for right
 * @param scrollState Horizontal scroll state to mutate
 * @param isClosing Returns true when the application is shutting down and pointer events should be
 *   ignored
 * @param modifier Modifier applied to the arrow root
 */
@Composable
internal fun ScrollArrow(
    direction: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    isClosing: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val icon = if (direction < 0) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight
    val onClick = remember(direction, scrollState, scope) { { scrollArrowHandler(direction, scrollState, scope, isClosing) } }
    Box(
        modifier = modifier.padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        ActionIconButton(
            icon = icon,
            description = if (direction < 0) "Scroll left" else "Scroll right",
            onClick = onClick,
            modifier =
                Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC23252F))
                    .border(1.dp, Color(0x558BE9FD), RoundedCornerShape(10.dp)),
            iconSize = 32.dp,
        )
    }
}

private const val SCROLL_ARROW_STEP_PX = 120
private const val HORIZONTAL_WHEEL_SCROLL_STEP = 50
