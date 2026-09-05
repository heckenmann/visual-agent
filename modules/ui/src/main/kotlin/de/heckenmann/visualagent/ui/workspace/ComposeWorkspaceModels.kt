@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.workspace

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
 * Computes the total horizontal extent of the workspace row.
 *
 * The returned value uses the same density-independent units as panel widths and includes
 * the leading workspace gap, every panel and resizer, and the gaps between adjacent panels.
 *
 * @param widths Effective widths of visible panels, including any active resize preview
 * @return Total row width, or zero when no panels are visible
 */
internal fun workspaceRowWidth(widths: List<Int>): Int {
    if (widths.isEmpty()) return 0
    return WORKSPACE_PANEL_GAP +
        widths.sum() +
        (widths.size * WORKSPACE_PANEL_RESIZER_WIDTH) +
        ((widths.size - 1) * WORKSPACE_PANEL_GAP)
}

/**
 * Computes a new preferred width for a panel after a resizer drag.
 *
 * The delta is applied to the current width and clamped to the allowed range.
 * Unlike the previous adjacent-panel resize, this does not shrink the neighbour;
 * it only changes the resized panel, so all panels to the right are pushed right.
 *
 * @param currentWidth Current panel width in density-independent units
 * @param deltaWidth Horizontal delta in density-independent units
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
 * Width of the draggable resizer handle rendered on the right edge of each panel.
 *
 * This value must stay in sync with the visual width used by [panelResizer] so that
 * scroll and layout math match the rendered content.
 */
const val WORKSPACE_PANEL_RESIZER_WIDTH: Int = 12
