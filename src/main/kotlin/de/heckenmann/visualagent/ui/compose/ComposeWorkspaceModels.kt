package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import kotlin.math.max

/**
 * Bounds for a workspace panel in the Compose Multiplatform workspace.
 *
 * Coordinates and sizes are expressed in density-independent units to keep the model
 * independent from a concrete UI toolkit.
 */
data class ComposeWorkspaceWindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    /**
     * Move these bounds by a delta while keeping them inside the given viewport.
     *
     * @param deltaX Horizontal movement delta
     * @param deltaY Vertical movement delta
     * @param viewport Available workspace bounds
     * @return Clamped window bounds after movement
     */
    fun moveBy(
        deltaX: Int,
        deltaY: Int,
        viewport: ComposeWorkspaceViewport,
    ): ComposeWorkspaceWindowBounds = copy(x = x + deltaX, y = y + deltaY).coerceIn(viewport)

    /**
     * Resize these bounds while enforcing the minimum size and viewport boundaries.
     *
     * @param deltaWidth Width change
     * @param deltaHeight Height change
     * @param viewport Available workspace bounds
     * @return Clamped window bounds after resizing
     */
    fun resizeBy(
        deltaWidth: Int,
        deltaHeight: Int,
        viewport: ComposeWorkspaceViewport,
    ): ComposeWorkspaceWindowBounds =
        copy(width = width + deltaWidth, height = height + deltaHeight)
            .coerceMinimumSize()
            .coerceIn(viewport)

    /**
     * Clamp these bounds so they remain visible inside the viewport.
     *
     * @param viewport Available workspace bounds
     * @return Window bounds that fit into the viewport
     */
    fun coerceIn(viewport: ComposeWorkspaceViewport): ComposeWorkspaceWindowBounds {
        val constrained = coerceMinimumSize()
        val maxWidth = max(MIN_WIDTH, viewport.width)
        val maxHeight = max(MIN_HEIGHT, viewport.height)
        val nextWidth = constrained.width.coerceAtMost(maxWidth)
        val nextHeight = constrained.height.coerceAtMost(maxHeight)
        val maxX = max(0, viewport.width - nextWidth)
        val maxY = max(0, viewport.height - nextHeight)
        return constrained.copy(
            x = constrained.x.coerceIn(0, maxX),
            y = constrained.y.coerceIn(0, maxY),
            width = nextWidth,
            height = nextHeight,
        )
    }

    private fun coerceMinimumSize(): ComposeWorkspaceWindowBounds =
        copy(
            width = width.coerceAtLeast(MIN_WIDTH),
            height = height.coerceAtLeast(MIN_HEIGHT),
        )

    companion object {
        /** Minimum workspace panel width used by the Compose workspace. */
        const val MIN_WIDTH: Int = 280

        /** Minimum workspace panel height used by the Compose workspace. */
        const val MIN_HEIGHT: Int = 180
    }
}

/**
 * Available workspace dimensions for Compose workspace panel calculations.
 */
data class ComposeWorkspaceViewport(
    val width: Int,
    val height: Int,
)

/**
 * Describes one panel displayed by the Compose Multiplatform workspace.
 */
data class ComposeWorkspaceWindow(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val bounds: ComposeWorkspaceWindowBounds,
    val visible: Boolean = true,
)

/**
 * Direction for moving a workspace panel within the user-defined panel order.
 */
enum class ComposePanelMoveDirection {
    /** Move the panel closer to the primary stage position. */
    Earlier,

    /** Move the panel farther away from the primary stage position. */
    Later,
}

/**
 * Restores persisted visibility and ordering onto the current workspace panel descriptors.
 *
 * Missing persisted IDs are ignored, and new default panels are appended in their default order.
 *
 * @param defaults Current panel descriptors shipped by the application
 * @param persisted Persisted panel states from the workspace layout service
 * @return Panels ordered and marked visible according to persisted state where available
 */
fun restoreWorkspaceWindows(
    defaults: List<ComposeWorkspaceWindow>,
    persisted: List<WorkspaceWindowState>,
): List<ComposeWorkspaceWindow> {
    if (persisted.isEmpty()) return defaults
    val persistedById = persisted.associateBy { it.id }
    return defaults
        .mapIndexed { defaultIndex, window ->
            val persistedState = persistedById[window.id]
            val restoredBounds =
                persistedState?.let {
                    ComposeWorkspaceWindowBounds(
                        x = it.x.toInt(),
                        y = it.y.toInt(),
                        width = it.width.toInt(),
                        height = it.height.toInt(),
                    )
                } ?: window.bounds
            window.copy(
                bounds = restoredBounds,
                visible = persistedState?.visible ?: window.visible,
            ) to
                PanelSortKey(
                    persistedOrder = persistedState?.zIndex ?: (Int.MAX_VALUE - defaults.size + defaultIndex),
                    defaultOrder = defaultIndex,
                )
        }.sortedWith(compareBy({ it.second.persistedOrder }, { it.second.defaultOrder }))
        .map { it.first }
}

private data class PanelSortKey(
    val persistedOrder: Int,
    val defaultOrder: Int,
)

/**
 * Moves a workspace panel earlier or later in the user-defined panel order.
 *
 * @param windows Current panel order
 * @param id Panel ID to move
 * @param direction Direction to move
 * @return Updated panel order, or the original order when movement is not possible
 */
fun moveWorkspacePanel(
    windows: List<ComposeWorkspaceWindow>,
    id: String,
    direction: ComposePanelMoveDirection,
): List<ComposeWorkspaceWindow> {
    val index = windows.indexOfFirst { it.id == id }
    val targetIndex =
        when (direction) {
            ComposePanelMoveDirection.Earlier -> index - 1
            ComposePanelMoveDirection.Later -> index + 1
        }
    if (index !in windows.indices || targetIndex !in windows.indices) return windows
    return windows.toMutableList().also { mutable ->
        val panel = mutable.removeAt(index)
        mutable.add(targetIndex, panel)
    }
}

/**
 * Toggles the visible state of a workspace panel.
 *
 * @param windows Current panels
 * @param id Panel ID to toggle
 * @return Updated panels, or the original list when the ID is unknown
 */
fun toggleWorkspacePanel(
    windows: List<ComposeWorkspaceWindow>,
    id: String,
): List<ComposeWorkspaceWindow> =
    windows.map { window ->
        if (window.id == id) {
            window.copy(visible = !window.visible)
        } else {
            window
        }
    }

/**
 * Resizes a workspace panel preference while keeping the requested size within the viewport.
 *
 * The semantic split layout uses these stored sizes as proportions for visible panels,
 * and the workspace layout tool persists the same values for model-driven changes.
 *
 * @param windows Current panels
 * @param id Panel ID to resize
 * @param deltaWidth Requested width delta
 * @param deltaHeight Requested height delta
 * @param viewport Available workspace dimensions
 * @return Updated panels, or the original list when the ID is unknown
 */
fun resizeWorkspacePanel(
    windows: List<ComposeWorkspaceWindow>,
    id: String,
    deltaWidth: Int,
    deltaHeight: Int,
    viewport: ComposeWorkspaceViewport,
): List<ComposeWorkspaceWindow> =
    windows.map { window ->
        if (window.id == id) {
            window.copy(bounds = window.bounds.resizeBy(deltaWidth, deltaHeight, viewport))
        } else {
            window
        }
    }

/**
 * Calculates deterministic designer-curated panel bounds for all visible workspace panels.
 *
 * The layout is intentionally semantic instead of count-based: the first visible
 * panel in the user-defined order receives the largest primary stage for compact
 * workspaces. Larger panel sets switch to balanced columns so every panel remains readable.
 *
 * @param windows Workspace panels in their visual order
 * @param viewport Available workspace dimensions
 * @return Bounds keyed by panel ID for visible panels only
 */
fun splitWorkspaceBounds(
    windows: List<ComposeWorkspaceWindow>,
    viewport: ComposeWorkspaceViewport,
): Map<String, ComposeWorkspaceWindowBounds> {
    val visibleWindows = windows.filter { it.visible }
    if (visibleWindows.isEmpty()) return emptyMap()
    val safeWidth = viewport.width.coerceAtLeast(1)
    val safeHeight = viewport.height.coerceAtLeast(1)
    val primary = visibleWindows.first()
    val supporting = visibleWindows.drop(1)

    return when (supporting.size) {
        0 ->
            mapOf(
                primary.id to
                    ComposeWorkspaceWindowBounds(x = 0, y = 0, width = safeWidth, height = safeHeight),
            )
        1 ->
            splitStageWithInspector(
                primary = primary,
                inspectorPanels = supporting,
                width = safeWidth,
                height = safeHeight,
            )
        2, 3 ->
            splitStageWithInspector(
                primary = primary,
                inspectorPanels = supporting,
                width = safeWidth,
                height = safeHeight,
            )
        else ->
            splitBalancedColumns(
                windows = visibleWindows,
                width = safeWidth,
                height = safeHeight,
            )
    }
}

private fun splitStageWithInspector(
    primary: ComposeWorkspaceWindow,
    inspectorPanels: List<ComposeWorkspaceWindow>,
    width: Int,
    height: Int,
): Map<String, ComposeWorkspaceWindowBounds> {
    val inspectorWidth = inspectorWidth(width, primary, inspectorPanels)
    val stageWidth = (width - WORKSPACE_PANEL_GAP - inspectorWidth).coerceAtLeast(1)
    val result =
        mutableMapOf(
            primary.id to ComposeWorkspaceWindowBounds(x = 0, y = 0, width = stageWidth, height = height),
        )
    result += stackVertically(inspectorPanels, x = stageWidth + WORKSPACE_PANEL_GAP, y = 0, width = inspectorWidth, height = height)
    return result
}

private fun splitBalancedColumns(
    windows: List<ComposeWorkspaceWindow>,
    width: Int,
    height: Int,
): Map<String, ComposeWorkspaceWindowBounds> {
    val leftCount = (windows.size + 1) / 2
    val leftPanels = windows.take(leftCount)
    val rightPanels = windows.drop(leftCount)
    val leftWidth = ((width - WORKSPACE_PANEL_GAP) / 2).coerceAtLeast(1)
    val rightWidth = (width - WORKSPACE_PANEL_GAP - leftWidth).coerceAtLeast(1)
    val result = mutableMapOf<String, ComposeWorkspaceWindowBounds>()
    result += stackVertically(leftPanels, x = 0, y = 0, width = leftWidth, height = height, preferStoredSizes = false)
    result +=
        stackVertically(
            rightPanels,
            x = leftWidth + WORKSPACE_PANEL_GAP,
            y = 0,
            width = rightWidth,
            height = height,
            preferStoredSizes = false,
        )
    return result
}

private fun stackVertically(
    windows: List<ComposeWorkspaceWindow>,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    preferStoredSizes: Boolean = true,
): Map<String, ComposeWorkspaceWindowBounds> =
    splitLinear(windows, x, y, width, height, vertical = true, preferStoredSizes = preferStoredSizes)

private fun splitLinear(
    windows: List<ComposeWorkspaceWindow>,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    vertical: Boolean,
    preferStoredSizes: Boolean,
): Map<String, ComposeWorkspaceWindowBounds> {
    if (windows.isEmpty()) return emptyMap()
    val gapTotal = WORKSPACE_PANEL_GAP * (windows.size - 1)
    val available = ((if (vertical) height else width) - gapTotal).coerceAtLeast(windows.size)
    val preferredSizes =
        if (preferStoredSizes) {
            windows.map { window ->
                if (vertical) {
                    window.bounds.height.takeIf {
                        it >= ComposeWorkspaceWindowBounds.MIN_HEIGHT
                    }
                } else {
                    window.bounds.width.takeIf {
                        it >= ComposeWorkspaceWindowBounds.MIN_WIDTH
                    }
                }
            }
        } else {
            List(windows.size) { null }
        }
    val resolvedSizes =
        if (preferredSizes.all { it != null }) {
            proportionalSizes(preferredSizes.filterNotNull(), available)
        } else {
            equalSizes(windows.size, available)
        }
    return windows
        .mapIndexed { index, window ->
            val offset = resolvedSizes.take(index).sum() + (WORKSPACE_PANEL_GAP * index)
            val isLast = index == windows.lastIndex
            val cellWidth =
                if (vertical) {
                    width
                } else if (isLast) {
                    width - offset
                } else {
                    resolvedSizes[index]
                }
            val cellHeight =
                if (vertical) {
                    if (isLast) height - offset else resolvedSizes[index]
                } else {
                    height
                }
            window.id to
                ComposeWorkspaceWindowBounds(
                    x = if (vertical) x else x + offset,
                    y = if (vertical) y + offset else y,
                    width = cellWidth.coerceAtLeast(1),
                    height = cellHeight.coerceAtLeast(1),
                )
        }.toMap()
}

private fun inspectorWidth(
    width: Int,
    primary: ComposeWorkspaceWindow,
    inspectorPanels: List<ComposeWorkspaceWindow>,
): Int {
    val available = (width - WORKSPACE_PANEL_GAP).coerceAtLeast(1)
    val preferredPrimaryWidth = primary.bounds.width.takeIf { it >= ComposeWorkspaceWindowBounds.MIN_WIDTH }
    val preferredInspectorWidth =
        inspectorPanels
            .mapNotNull { panel ->
                panel.bounds.width.takeIf { value ->
                    value >= ComposeWorkspaceWindowBounds.MIN_WIDTH
                }
            }.maxOrNull()
    val requested =
        when {
            preferredPrimaryWidth != null && preferredInspectorWidth != null ->
                ((available.toDouble() * preferredInspectorWidth) / (preferredPrimaryWidth + preferredInspectorWidth)).toInt()
            preferredPrimaryWidth != null -> available - preferredPrimaryWidth
            preferredInspectorWidth != null -> preferredInspectorWidth
            else -> (width * 0.32f).toInt()
        }
    return requested
        .coerceIn(320, max(320, width - WORKSPACE_PANEL_GAP - 520))
}

private fun equalSizes(
    count: Int,
    available: Int,
): List<Int> {
    val baseSize = (available / count).coerceAtLeast(1)
    return List(count) { baseSize }
}

private fun proportionalSizes(
    preferredSizes: List<Int>,
    available: Int,
): List<Int> {
    val total = preferredSizes.sum().coerceAtLeast(1)
    val minimum = if (preferredSizes.size == 1) available else 1
    return preferredSizes.map { size -> ((available.toDouble() * size) / total).toInt().coerceAtLeast(minimum) }
}

/**
 * Gap used by the semantic workspace layout.
 */
const val WORKSPACE_PANEL_GAP: Int = 16

/**
 * Spring-backed services required by Compose panels.
 *
 * Keeping this bundle explicit avoids hidden global lookups from individual composables.
 */
data class ComposePanelServices(
    val config: AppConfig,
    val agentManager: AgentManager,
    val llmProvider: LLMProvider,
    val providerCatalogService: ProviderCatalogService,
    val agentToolConfigService: AgentToolConfigService,
    val toolRegistry: ToolRegistry,
    val workspaceFileService: WorkspaceFileService,
    val canvasOperations: CanvasOperations,
    val modalRequester: ComposeModalRequester,
    val onSettingsChanged: () -> Unit,
)
