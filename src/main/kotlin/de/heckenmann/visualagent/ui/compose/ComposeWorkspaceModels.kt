package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
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
 *
 * @property preferredWidth User-defined minimum width for the panel in the row layout.
 *   Defaults to the panel's initial bounds width clamped to the minimum.
 */
data class ComposeWorkspaceWindow(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val bounds: ComposeWorkspaceWindowBounds,
    val visible: Boolean = true,
    val preferredWidth: Int = bounds.width.coerceAtLeast(ComposeWorkspaceWindowBounds.MIN_WIDTH),
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
            val restoredPreferredWidth =
                persistedState
                    ?.preferredWidth
                    ?.takeIf { it > 0 }
                    ?.toInt()
                    ?.coerceAtLeast(ComposeWorkspaceWindowBounds.MIN_WIDTH)
                    ?: window.preferredWidth
            window.copy(
                visible = persistedState?.visible ?: window.visible,
                preferredWidth = restoredPreferredWidth,
            ) to
                PanelSortKey(
                    persistedOrder = persistedState?.order ?: (Int.MAX_VALUE - defaults.size + defaultIndex),
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
 * Moves one workspace panel to the position of another panel.
 *
 * @param windows Current panel order
 * @param draggedId Panel being dragged
 * @param targetId Panel currently used as the drop/reorder target
 * @return Updated panel order, or the original list when either ID is unknown
 */
fun reorderWorkspacePanel(
    windows: List<ComposeWorkspaceWindow>,
    draggedId: String,
    targetId: String,
): List<ComposeWorkspaceWindow> {
    if (draggedId == targetId) return windows
    val fromIndex = windows.indexOfFirst { it.id == draggedId }
    val targetIndex = windows.indexOfFirst { it.id == targetId }
    if (fromIndex !in windows.indices || targetIndex !in windows.indices) return windows
    return windows.toMutableList().also { mutable ->
        val dragged = mutable.removeAt(fromIndex)
        val insertionIndex = targetIndex.coerceIn(0, mutable.size)
        mutable.add(insertionIndex, dragged)
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
 * Computes panel widths for a single horizontal row.
 *
 * Each visible panel uses its own [ComposeWorkspaceWindow.preferredWidth] clamped to
 * the global minimum. The row becomes horizontally scrollable when the combined widths
 * (plus gaps) exceed the viewport. Widths are attached to the panel identity, not to
 * the position, so reordering does not change panel sizes.
 *
 * @param visibleWindows Visible panels in row order
 * @return Width for each panel in row order
 */
fun rowPanelWidths(visibleWindows: List<ComposeWorkspaceWindow>): List<Int> =
    visibleWindows.map { it.preferredWidth.coerceAtLeast(ComposeWorkspaceWindowBounds.MIN_WIDTH) }

/**
 * Computes a new preferred width for a panel after a resizer drag.
 *
 * The delta is applied to the current width and clamped to the allowed range.
 * Unlike the previous adjacent-panel resize, this does not shrink the neighbour;
 * it only changes the resized panel, so all panels to the right are pushed right.
 *
 * @param currentWidth Current panel width in pixels
 * @param deltaWidth Horizontal delta in pixels
 * @param minPanelWidth Minimum width the panel must keep
 * @param maxPanelWidth Maximum width the panel may reach
 * @return Clamped new width
 */
fun resizePanelWidth(
    currentWidth: Int,
    deltaWidth: Int,
    minPanelWidth: Int,
    maxPanelWidth: Int,
): Int = (currentWidth + deltaWidth).coerceIn(minPanelWidth, maxPanelWidth)

/**
 * Gap used by the horizontal workspace row between panels and resizers.
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
    val toolEventBus: ToolEventBus,
    val workspaceFileService: WorkspaceFileService,
    val canvasOperations: CanvasOperations,
    val modalRequester: ComposeModalRequester,
    val onSettingsChanged: () -> Unit,
    val inFlight: InFlightStateHolder,
)
