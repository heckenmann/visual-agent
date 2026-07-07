package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeWorkspaceModelsTest {
    private val testMinWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH

    @Test
    fun `row panel widths returns preferred widths attached to panel identity`() {
        val chat = testWindow("chat", preferredWidth = 320)
        val todos = testWindow("todos", preferredWidth = 340)
        val files = testWindow("files", preferredWidth = 400)

        val widths = rowPanelWidths(listOf(chat, todos, files))

        assertEquals(listOf(320, 340, 400), widths)
    }

    @Test
    fun `row panel widths clamps widths below minimum`() {
        val windows = listOf(testWindow("chat", preferredWidth = 100), testWindow("todos", preferredWidth = 500))

        val widths = rowPanelWidths(windows)

        assertEquals(listOf(testMinWidth, 500), widths)
    }

    @Test
    fun `row panel widths preserves panel widths after reordering`() {
        val chat = testWindow("chat", preferredWidth = 320)
        val todos = testWindow("todos", preferredWidth = 340)
        val files = testWindow("files", preferredWidth = 400)

        val reordered = rowPanelWidths(listOf(files, chat, todos))

        assertEquals(listOf(400, 320, 340), reordered)
    }

    @Test
    fun `resize panel width applies delta and clamps`() {
        assertEquals(500, resizePanelWidth(400, 100, minPanelWidth = 280, maxPanelWidth = 800))
        assertEquals(280, resizePanelWidth(400, -200, minPanelWidth = 280, maxPanelWidth = 800))
        assertEquals(800, resizePanelWidth(700, 200, minPanelWidth = 280, maxPanelWidth = 800))
    }

    @Test
    fun `resize enforces minimum size and viewport maximums`() {
        val viewport = ComposeWorkspaceViewport(width = 640, height = 480)
        val bounds = ComposeWorkspaceWindowBounds(x = 100, y = 100, width = 500, height = 400)

        val smaller = bounds.resizeBy(-400, -300, viewport)
        val larger = bounds.resizeBy(400, 400, viewport)

        assertEquals(280, smaller.width)
        assertEquals(180, smaller.height)
        assertEquals(640, larger.width)
        assertEquals(480, larger.height)
        assertEquals(0, larger.x)
        assertEquals(0, larger.y)
    }

    @Test
    fun `coerce keeps oversized viewport edge cases valid`() {
        val viewport = ComposeWorkspaceViewport(width = 120, height = 90)
        val bounds = ComposeWorkspaceWindowBounds(x = -50, y = -40, width = 40, height = 30)

        val coerced = bounds.coerceIn(viewport)

        assertEquals(0, coerced.x)
        assertEquals(0, coerced.y)
        assertEquals(280, coerced.width)
        assertEquals(180, coerced.height)
    }

    @Test
    fun `resize workspace panel stores bounded size preference`() {
        val windows = listOf(testWindow("chat", width = 500, height = 400), testWindow("todos"))

        val resized = resizeWorkspacePanel(windows, "chat", 300, 300, ComposeWorkspaceViewport(width = 640, height = 480))
        val unchanged = resizeWorkspacePanel(windows, "missing", 300, 300, ComposeWorkspaceViewport(width = 640, height = 480))

        assertEquals(640, resized.first { it.id == "chat" }.bounds.width)
        assertEquals(480, resized.first { it.id == "chat" }.bounds.height)
        assertEquals(windows, unchanged)
    }

    @Test
    fun `move workspace panel changes order within bounds`() {
        val windows = listOf("chat", "todos", "files").map(::testWindow)

        val movedEarlier = moveWorkspacePanel(windows, "files", ComposePanelMoveDirection.Earlier)
        val movedLater = moveWorkspacePanel(movedEarlier, "files", ComposePanelMoveDirection.Later)
        val unchanged = moveWorkspacePanel(windows, "chat", ComposePanelMoveDirection.Earlier)

        assertEquals(listOf("chat", "files", "todos"), movedEarlier.map { it.id })
        assertEquals(windows.map { it.id }, movedLater.map { it.id })
        assertEquals(windows, unchanged)
    }

    @Test
    fun `toggle workspace panel switches visibility without changing order`() {
        val windows = listOf(testWindow("chat", visible = true), testWindow("todos", visible = false))

        val chatHidden = toggleWorkspacePanel(windows, "chat")
        val todosVisible = toggleWorkspacePanel(chatHidden, "todos")

        assertEquals(listOf("chat", "todos"), todosVisible.map { it.id })
        assertFalse(chatHidden.first { it.id == "chat" }.visible)
        assertTrue(todosVisible.first { it.id == "todos" }.visible)
        assertEquals(windows, toggleWorkspacePanel(windows, "missing"))
    }

    @Test
    fun `reorder workspace panel moves dragged panel to target slot`() {
        val windows = listOf("chat", "todos", "files", "agents").map(::testWindow)

        val reordered = reorderWorkspacePanel(windows, draggedId = "files", targetId = "chat")
        val draggedOntoSelf = reorderWorkspacePanel(windows, draggedId = "chat", targetId = "chat")
        val unknownTarget = reorderWorkspacePanel(windows, draggedId = "files", targetId = "missing")
        val unknownDragged = reorderWorkspacePanel(windows, draggedId = "missing", targetId = "chat")

        assertEquals(listOf("files", "chat", "todos", "agents"), reordered.map { it.id })
        assertEquals(windows, draggedOntoSelf)
        assertEquals(windows, unknownTarget)
        assertEquals(windows, unknownDragged)
    }

    @Test
    fun `restore workspace windows applies persisted order visibility and preferred width`() {
        val defaults = listOf("chat", "todos", "files").map(::testWindow)
        val persisted =
            listOf(
                persistedWindow("files", order = 0, visible = true, preferredWidth = 360.0),
                persistedWindow("chat", order = 1, visible = false, preferredWidth = 520.0),
            )

        val restored = restoreWorkspaceWindows(defaults, persisted)

        assertEquals(listOf("files", "chat", "todos"), restored.map { it.id })
        assertEquals(true, restored[0].visible)
        assertEquals(false, restored[1].visible)
        assertEquals(true, restored[2].visible)
        assertEquals(360, restored[0].preferredWidth)
        assertEquals(520, restored[1].preferredWidth)
    }

    @Test
    fun `restore workspace windows keeps defaults when persisted width is zero`() {
        val defaults = listOf(testWindow("chat", width = 520), testWindow("todos", width = 420))
        val persisted = listOf(persistedWindow("chat", order = 0, visible = true, preferredWidth = 0.0))

        val restored = restoreWorkspaceWindows(defaults, persisted)

        assertEquals(520, restored.first { it.id == "chat" }.preferredWidth)
    }

    @Test
    fun `restore workspace windows returns defaults when persisted is empty`() {
        val defaults = listOf("chat", "todos").map(::testWindow)

        val restored = restoreWorkspaceWindows(defaults, emptyList())

        assertEquals(defaults, restored)
    }

    @Test
    fun `moveBy clamps position inside viewport`() {
        val viewport = ComposeWorkspaceViewport(width = 640, height = 480)
        val bounds = ComposeWorkspaceWindowBounds(x = 100, y = 100, width = 500, height = 400)

        val moved = bounds.moveBy(1000, 1000, viewport)

        assertEquals(140, moved.x)
        assertEquals(80, moved.y)
        assertEquals(500, moved.width)
        assertEquals(400, moved.height)
    }

    @Test
    fun `workspace window stores preferred width from bounds`() {
        val window = testWindow("chat", width = 300)

        assertEquals(300, window.preferredWidth)
    }

    @Test
    fun `workspace window coerces preferred width to minimum`() {
        val window = testWindow("chat", width = 100)

        assertEquals(testMinWidth, window.preferredWidth)
    }

    @Test
    fun `row panel widths handles empty list`() {
        assertEquals(emptyList(), rowPanelWidths(emptyList()))
    }

    @Test
    fun `toggle returns original list for unknown id`() {
        val windows = listOf(testWindow("chat"))

        assertEquals(windows, toggleWorkspacePanel(windows, "missing"))
    }

    private fun testWindow(
        id: String,
        visible: Boolean = true,
        width: Int = 100,
        height: Int = 100,
        preferredWidth: Int = width.coerceAtLeast(ComposeWorkspaceWindowBounds.MIN_WIDTH),
    ): ComposeWorkspaceWindow =
        ComposeWorkspaceWindow(
            id = id,
            icon = id.take(1),
            title = id,
            subtitle = id,
            bounds = ComposeWorkspaceWindowBounds(x = 0, y = 0, width = width, height = height),
            visible = visible,
            preferredWidth = preferredWidth,
        )

    private fun persistedWindow(
        id: String,
        order: Int,
        visible: Boolean,
        preferredWidth: Double,
    ): WorkspaceWindowState =
        WorkspaceWindowState(
            id = id,
            order = order,
            visible = visible,
            preferredWidth = preferredWidth,
        )
}
