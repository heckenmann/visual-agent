package de.heckenmann.visualagent.ui

import javafx.scene.Node
import javafx.scene.layout.Pane

/**
 * Owns the internal desktop windows for all primary workspace panels.
 */
internal class WorkspaceWindowManager(
    private val desktop: Pane,
) {
    private val entries = mutableListOf<WorkspaceWindowEntry>()
    private val entriesByPanel = LinkedHashMap<Node, WorkspaceWindowEntry>()

    init {
        desktop.widthProperty().addListener { _, _, _ -> keepWindowsInsideDesktop() }
        desktop.heightProperty().addListener { _, _, _ -> keepWindowsInsideDesktop() }
    }

    fun register(
        id: String,
        title: String,
        iconLiteral: String,
        panel: Node,
        placement: WindowPlacement,
    ) {
        val window = InternalWorkspaceWindow(title, iconLiteral, panel)
        window.place(placement.x, placement.y, placement.width, placement.height)
        window.isVisible = false
        window.isManaged = false
        val entry = WorkspaceWindowEntry(id, panel, window)
        entries.add(entry)
        entriesByPanel[panel] = entry
        desktop.children.add(window)
    }

    fun focus(panel: Node) {
        entries.forEach { entry ->
            val window = entry.window
            val registeredPanel = entry.panel
            val active = registeredPanel === panel
            if (active) {
                window.isVisible = true
                window.isManaged = true
                keepWindowInsideDesktop(window)
            }
            window.setActive(active)
            if (active) {
                window.toFront()
            }
        }
    }

    fun restore(layout: WorkspaceLayout) {
        val statesById = layout.windows.associateBy(WorkspaceWindowState::id)
        entries.forEach { entry ->
            val state = statesById[entry.id] ?: return@forEach
            entry.window.place(state.x, state.y, state.width, state.height)
            keepWindowInsideDesktop(entry.window)
            entry.window.isVisible = state.visible
            entry.window.isManaged = state.visible
        }
        layout.windows.sortedBy(WorkspaceWindowState::zIndex).forEach { state ->
            entries.find { it.id == state.id }?.window?.toFront()
        }
    }

    fun snapshot(): WorkspaceLayout {
        val zOrder = desktop.children.withIndex().associate { (index, node) -> node to index }
        return WorkspaceLayout(
            entries.map { entry ->
                entry.window.toState(entry.id, zOrder[entry.window] ?: 0)
            },
        )
    }

    fun windowFor(panel: Node): InternalWorkspaceWindow? = entriesByPanel[panel]?.window

    fun desktopSize(): Pair<Double, Double> = desktop.width to desktop.height

    private fun keepWindowsInsideDesktop() {
        entries.forEach { entry -> keepWindowInsideDesktop(entry.window) }
    }

    private fun keepWindowInsideDesktop(window: InternalWorkspaceWindow) {
        if (desktop.width > 0.0 && desktop.height > 0.0) {
            window.keepInside(desktop.width, desktop.height)
        }
    }
}

internal data class WindowPlacement(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

private data class WorkspaceWindowEntry(
    val id: String,
    val panel: Node,
    val window: InternalWorkspaceWindow,
)

private fun InternalWorkspaceWindow.toState(
    id: String,
    zIndex: Int,
): WorkspaceWindowState =
    WorkspaceWindowState(
        id = id,
        x = layoutX,
        y = layoutY,
        width = if (width > 0.0) width else prefWidth,
        height = if (height > 0.0) height else prefHeight,
        visible = isVisible,
        zIndex = zIndex,
    )
