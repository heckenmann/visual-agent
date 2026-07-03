package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeWorkspaceModelsTest {
    private val testMinWidth = ComposeWorkspaceWindowBounds.MIN_WIDTH

    @Test
    fun `split workspace ignores hidden panels`() {
        val windows =
            listOf(
                testWindow("chat", visible = true),
                testWindow("todos", visible = false),
            )

        val bounds = splitWorkspaceBounds(windows, ComposeWorkspaceViewport(width = 800, height = 600), testMinWidth)

        assertEquals(setOf("chat"), bounds.keys)
        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 800, height = 600), bounds["chat"])
    }

    @Test
    fun `split workspace lays out three panels as stage with inspector stack`() {
        val windows = listOf(testWindow("chat"), testWindow("todos"), testWindow("files"))

        val bounds = splitWorkspaceBounds(windows, ComposeWorkspaceViewport(width = 1000, height = 700), testMinWidth)

        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 492, height = 700), bounds["chat"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 508, y = 0, width = 492, height = 342), bounds["todos"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 508, y = 358, width = 492, height = 342), bounds["files"])
    }

    @Test
    fun `split workspace lays out larger panel sets as balanced columns`() {
        val windows = listOf("chat", "todos", "files", "agents", "settings", "canvas").map(::testWindow)

        val bounds = splitWorkspaceBounds(windows, ComposeWorkspaceViewport(width = 1200, height = 800), testMinWidth)

        assertEquals(6, bounds.size)
        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 592, height = 256), bounds["chat"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 272, width = 592, height = 256), bounds["todos"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 544, width = 592, height = 256), bounds["files"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 608, y = 0, width = 592, height = 256), bounds["agents"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 608, y = 272, width = 592, height = 256), bounds["settings"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 608, y = 544, width = 592, height = 256), bounds["canvas"])
        assertFalse(bounds.containsKey("unknown"))
    }

    @Test
    fun `split workspace uses user-defined order for primary stage`() {
        val windows = listOf("canvas", "chat", "todos").map(::testWindow)

        val bounds = splitWorkspaceBounds(windows, ComposeWorkspaceViewport(width = 1000, height = 700), testMinWidth)

        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 492, height = 700), bounds["canvas"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 508, y = 0, width = 492, height = 342), bounds["chat"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 508, y = 358, width = 492, height = 342), bounds["todos"])
    }

    @Test
    fun `split workspace uses stored panel sizes as resize preferences`() {
        val windows =
            listOf(
                testWindow("chat", width = 620, height = 500),
                testWindow("todos", width = 360, height = 500),
                testWindow("files", width = 360, height = 260),
            )

        val bounds = splitWorkspaceBounds(windows, ComposeWorkspaceViewport(width = 1000, height = 700), testMinWidth)

        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 623, height = 700), bounds["chat"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 639, y = 0, width = 361, height = 450), bounds["todos"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 639, y = 466, width = 361, height = 234), bounds["files"])
    }

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
    fun `restore workspace windows applies persisted order visibility and bounds`() {
        val defaults = listOf("chat", "todos", "files").map(::testWindow)
        val persisted =
            listOf(
                persistedWindow("files", zIndex = 0, visible = true, x = 10.0),
                persistedWindow("chat", zIndex = 1, visible = false, x = 20.0),
            )

        val restored = restoreWorkspaceWindows(defaults, persisted)

        assertEquals(listOf("files", "chat", "todos"), restored.map { it.id })
        assertEquals(true, restored[0].visible)
        assertEquals(false, restored[1].visible)
        assertEquals(true, restored[2].visible)
        assertEquals(10, restored[0].bounds.x)
        assertEquals(20, restored[1].bounds.x)
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
        zIndex: Int,
        visible: Boolean,
        x: Double,
    ): WorkspaceWindowState =
        WorkspaceWindowState(
            id = id,
            x = x,
            y = 0.0,
            width = 300.0,
            height = 200.0,
            visible = visible,
            zIndex = zIndex,
        )
}
