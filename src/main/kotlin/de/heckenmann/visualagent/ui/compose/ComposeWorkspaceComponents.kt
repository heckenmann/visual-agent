@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableListItemScope
import sh.calvin.reorderable.ReorderableRow
import sh.calvin.reorderable.ReorderableRowScope

/**
 * Renders the visible workspace panels in a single horizontal row.
 *
 * Every visible panel gets a column that spans the full row height. Panels are
 * separated by draggable resizer handles. The panel order can be changed by
 * dragging the panel header grip thanks to `sh.calvin.reorderable`.
 *
 * @param windows All workspace panels in persistent order
 * @param panelWidths Width for each visible panel in row order; updated by
 *   resizer gestures
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
                                hasResizer = index < visibleWindows.lastIndex,
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
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        ScrollArrow(
                            direction = 1,
                            scrollState = horizontalScrollState,
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
    hasResizer: Boolean,
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
            if (hasResizer) {
                PanelResizer(
                    currentWidth = width,
                    onWidthChanged = onWidthChanged,
                    minPanelWidth = minPanelWidth,
                )
            }
        }
    }
}

@Composable
private fun ReorderableListItemScope.SplitPanelContent(
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
private fun PanelResizer(
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
private const val SCROLL_ARROW_STEP_PX = 120
private const val HORIZONTAL_WHEEL_SCROLL_STEP = 50

@Composable
private fun ScrollArrow(
    direction: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val icon = if (direction < 0) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight
    Box(
        modifier = modifier.padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        ActionIconButton(
            icon = icon,
            description = if (direction < 0) "Scroll left" else "Scroll right",
            onClick = {
                scope.launch {
                    scrollState.animateScrollTo(
                        (scrollState.value + direction * SCROLL_ARROW_STEP_PX).coerceIn(0, scrollState.maxValue),
                    )
                }
            },
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

@Composable
private fun WindowBody(
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
