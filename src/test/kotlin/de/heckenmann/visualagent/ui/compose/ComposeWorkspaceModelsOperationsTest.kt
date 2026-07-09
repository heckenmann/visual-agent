package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeWorkspaceModelsOperationsTest {
    private val viewport = ComposeWorkspaceViewport(width = 1920, height = 1080)

    private fun window(
        id: String,
        x: Int = 0,
        y: Int = 0,
        width: Int = 300,
        height: Int = 200,
        visible: Boolean = true,
    ): ComposeWorkspaceWindow =
        ComposeWorkspaceWindow(
            id = id,
            icon = "icon",
            title = id,
            subtitle = "",
            bounds = ComposeWorkspaceWindowBounds(x, y, width, height),
            visible = visible,
        )

    @Test
    fun `bounds movement clamps to viewport`() {
        val bounds = ComposeWorkspaceWindowBounds(100, 100, 300, 200)
        assertEquals(ComposeWorkspaceWindowBounds(0, 0, 300, 200), bounds.moveBy(-200, -200, viewport))
        assertEquals(
            ComposeWorkspaceWindowBounds(
                viewport.width - 300,
                viewport.height - 200,
                300,
                200,
            ),
            bounds.moveBy(2000, 2000, viewport),
        )
    }

    @Test
    fun `bounds resize respects minimum size and viewport`() {
        val bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200)
        assertEquals(
            ComposeWorkspaceWindowBounds(0, 0, ComposeWorkspaceWindowBounds.MIN_WIDTH, ComposeWorkspaceWindowBounds.MIN_HEIGHT),
            bounds.resizeBy(-1000, -1000, viewport),
        )
        assertEquals(
            ComposeWorkspaceWindowBounds(0, 0, viewport.width, viewport.height),
            bounds.resizeBy(2000, 2000, viewport),
        )
    }

    @Test
    fun `restore workspace windows applies persisted visibility and order`() {
        val defaults = listOf(window("chat"), window("files"), window("todos"))
        val persisted =
            listOf(
                WorkspaceWindowState(id = "files", order = 0, visible = false, preferredWidth = 400.0),
                WorkspaceWindowState(id = "chat", order = 1, visible = true, preferredWidth = 350.0),
            )

        val restored = restoreWorkspaceWindows(defaults, persisted)

        assertEquals("files", restored[0].id)
        assertFalse(restored[0].visible)
        assertEquals(400, restored[0].preferredWidth)
        assertEquals("chat", restored[1].id)
        assertTrue(restored[1].visible)
        assertEquals("todos", restored[2].id)
    }

    @Test
    fun `restore with empty persisted returns defaults`() {
        val defaults = listOf(window("chat"))
        assertEquals(defaults, restoreWorkspaceWindows(defaults, emptyList()))
    }

    @Test
    fun `toggle workspace panel visibility`() {
        val windows = listOf(window("a", visible = true), window("b", visible = false))
        val toggled = toggleWorkspacePanel(windows, "a")
        assertFalse(toggled[0].visible)
        assertFalse(toggled[1].visible)
    }

    @Test
    fun `resize workspace panel updates bounds`() {
        val windows = listOf(window("a", width = 300, height = 200))
        val resized = resizeWorkspacePanel(windows, "a", 100, 100, viewport)
        assertEquals(400, resized.single().bounds.width)
        assertEquals(300, resized.single().bounds.height)
    }

    @Test
    fun `row panel widths enforce minimum`() {
        val windows = listOf(window("a", width = 100), window("b", width = 500))
        assertEquals(
            listOf(ComposeWorkspaceWindowBounds.MIN_WIDTH, 500),
            rowPanelWidths(windows),
        )
    }

    @Test
    fun `resize panel width clamps to bounds`() {
        assertEquals(100, resizePanelWidth(200, -500, 100, 400))
        assertEquals(400, resizePanelWidth(200, 500, 100, 400))
        assertEquals(250, resizePanelWidth(200, 50, 100, 400))
    }
}
