package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import kotlin.math.ceil
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
 * Available workspace dimensions for Compose internal-window calculations.
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
 * Calculates deterministic split-panel bounds for all visible workspace panels.
 *
 * The Compose desktop runtime does not provide a native split-window desktop. Using
 * split slots avoids overlap, drag jitter, and impossible resize states while
 * keeping the toolkit-neutral layout tool able to report concrete panel bounds.
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

    return when (visibleWindows.size) {
        1 ->
            mapOf(
                visibleWindows.single().id to
                    ComposeWorkspaceWindowBounds(x = 0, y = 0, width = safeWidth, height = safeHeight),
            )
        2 -> splitTwo(visibleWindows, safeWidth, safeHeight)
        3 -> splitThree(visibleWindows, safeWidth, safeHeight)
        else -> splitGrid(visibleWindows, safeWidth, safeHeight)
    }
}

private fun splitTwo(
    windows: List<ComposeWorkspaceWindow>,
    width: Int,
    height: Int,
): Map<String, ComposeWorkspaceWindowBounds> {
    val leftWidth = width / 2
    return mapOf(
        windows[0].id to ComposeWorkspaceWindowBounds(x = 0, y = 0, width = leftWidth, height = height),
        windows[1].id to ComposeWorkspaceWindowBounds(x = leftWidth, y = 0, width = width - leftWidth, height = height),
    )
}

private fun splitThree(
    windows: List<ComposeWorkspaceWindow>,
    width: Int,
    height: Int,
): Map<String, ComposeWorkspaceWindowBounds> {
    val leftWidth = width / 2
    val topHeight = height / 2
    return mapOf(
        windows[0].id to ComposeWorkspaceWindowBounds(x = 0, y = 0, width = leftWidth, height = height),
        windows[1].id to ComposeWorkspaceWindowBounds(x = leftWidth, y = 0, width = width - leftWidth, height = topHeight),
        windows[2].id to
            ComposeWorkspaceWindowBounds(
                x = leftWidth,
                y = topHeight,
                width = width - leftWidth,
                height = height - topHeight,
            ),
    )
}

private fun splitGrid(
    windows: List<ComposeWorkspaceWindow>,
    width: Int,
    height: Int,
): Map<String, ComposeWorkspaceWindowBounds> {
    val columns = 2
    val rows = ceil(windows.size.toDouble() / columns.toDouble()).toInt().coerceAtLeast(1)
    val columnWidth = width / columns
    val rowHeight = height / rows
    return windows
        .mapIndexed { index, window ->
            val column = index % columns
            val row = index / columns
            val x = column * columnWidth
            val y = row * rowHeight
            val cellWidth = if (column == columns - 1) width - x else columnWidth
            val cellHeight = if (row == rows - 1) height - y else rowHeight
            window.id to ComposeWorkspaceWindowBounds(x = x, y = y, width = cellWidth, height = cellHeight)
        }.toMap()
}

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
