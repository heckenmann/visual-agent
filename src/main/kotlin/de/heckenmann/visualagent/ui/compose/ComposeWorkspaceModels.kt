package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.AgentManager
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
 * Calculates deterministic designer-curated panel bounds for all visible workspace panels.
 *
 * The layout is intentionally semantic instead of count-based: the first visible
 * panel in the user-defined order receives the largest primary stage, supporting
 * panels are placed in an inspector column, and overflow panels move into a bottom
 * deck. This keeps the workspace stable without making all panels equally important.
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
            splitStageInspectorAndDeck(
                primary = primary,
                supporting = supporting,
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
    val inspectorWidth = inspectorWidth(width)
    val stageWidth = (width - WORKSPACE_PANEL_GAP - inspectorWidth).coerceAtLeast(1)
    val result =
        mutableMapOf(
            primary.id to ComposeWorkspaceWindowBounds(x = 0, y = 0, width = stageWidth, height = height),
        )
    result += stackVertically(inspectorPanels, x = stageWidth + WORKSPACE_PANEL_GAP, y = 0, width = inspectorWidth, height = height)
    return result
}

private fun splitStageInspectorAndDeck(
    primary: ComposeWorkspaceWindow,
    supporting: List<ComposeWorkspaceWindow>,
    width: Int,
    height: Int,
): Map<String, ComposeWorkspaceWindowBounds> {
    val deckHeight = (height * 0.28f).toInt().coerceIn(180, max(180, height / 2))
    val topHeight = (height - WORKSPACE_PANEL_GAP - deckHeight).coerceAtLeast(1)
    val inspectorWidth = inspectorWidth(width)
    val stageWidth = (width - WORKSPACE_PANEL_GAP - inspectorWidth).coerceAtLeast(1)
    val inspectorPanels = supporting.take(3)
    val deckPanels = supporting.drop(3)
    val result =
        mutableMapOf(
            primary.id to ComposeWorkspaceWindowBounds(x = 0, y = 0, width = stageWidth, height = topHeight),
        )
    result += stackVertically(inspectorPanels, x = stageWidth + WORKSPACE_PANEL_GAP, y = 0, width = inspectorWidth, height = topHeight)
    result += splitHorizontally(deckPanels, x = 0, y = topHeight + WORKSPACE_PANEL_GAP, width = width, height = deckHeight)
    return result
}

private fun stackVertically(
    windows: List<ComposeWorkspaceWindow>,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): Map<String, ComposeWorkspaceWindowBounds> = splitLinear(windows, x, y, width, height, vertical = true)

private fun splitHorizontally(
    windows: List<ComposeWorkspaceWindow>,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): Map<String, ComposeWorkspaceWindowBounds> = splitLinear(windows, x, y, width, height, vertical = false)

private fun splitLinear(
    windows: List<ComposeWorkspaceWindow>,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    vertical: Boolean,
): Map<String, ComposeWorkspaceWindowBounds> {
    if (windows.isEmpty()) return emptyMap()
    val gapTotal = WORKSPACE_PANEL_GAP * (windows.size - 1)
    val available = ((if (vertical) height else width) - gapTotal).coerceAtLeast(windows.size)
    val baseSize = (available / windows.size).coerceAtLeast(1)
    return windows
        .mapIndexed { index, window ->
            val offset = (baseSize + WORKSPACE_PANEL_GAP) * index
            val isLast = index == windows.lastIndex
            val cellWidth =
                if (vertical) {
                    width
                } else if (isLast) {
                    width - offset
                } else {
                    baseSize
                }
            val cellHeight =
                if (vertical) {
                    if (isLast) height - offset else baseSize
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

private fun inspectorWidth(width: Int): Int =
    (width * 0.32f)
        .toInt()
        .coerceIn(320, max(320, width - WORKSPACE_PANEL_GAP - 520))

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
    val workspaceFileService: WorkspaceFileService,
    val canvasOperations: CanvasOperations,
    val modalRequester: ComposeModalRequester,
)
