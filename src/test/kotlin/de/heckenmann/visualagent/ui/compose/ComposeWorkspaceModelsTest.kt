package de.heckenmann.visualagent.ui.compose

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ComposeWorkspaceModelsTest {
    @Test
    fun `move clamps window into viewport`() {
        val viewport = ComposeWorkspaceViewport(width = 800, height = 600)
        val bounds = ComposeWorkspaceWindowBounds(x = 700, y = 500, width = 240, height = 140)

        val moved = bounds.moveBy(120, 120, viewport)

        assertEquals(520, moved.x)
        assertEquals(420, moved.y)
        assertEquals(280, moved.width)
        assertEquals(180, moved.height)
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
    fun `split workspace ignores hidden panels`() {
        val windows =
            listOf(
                testWindow("chat", visible = true),
                testWindow("todos", visible = false),
            )

        val bounds = splitWorkspaceBounds(windows, ComposeWorkspaceViewport(width = 800, height = 600))

        assertEquals(setOf("chat"), bounds.keys)
        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 800, height = 600), bounds["chat"])
    }

    @Test
    fun `split workspace lays out three panels without overlap`() {
        val windows = listOf(testWindow("chat"), testWindow("todos"), testWindow("files"))

        val bounds = splitWorkspaceBounds(windows, ComposeWorkspaceViewport(width = 1000, height = 700))

        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 500, height = 700), bounds["chat"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 500, y = 0, width = 500, height = 350), bounds["todos"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 500, y = 350, width = 500, height = 350), bounds["files"])
    }

    @Test
    fun `split workspace lays out larger panel sets as two column grid`() {
        val windows = listOf("chat", "todos", "files", "agents", "settings").map(::testWindow)

        val bounds = splitWorkspaceBounds(windows, ComposeWorkspaceViewport(width = 900, height = 600))

        assertEquals(5, bounds.size)
        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 450, height = 200), bounds["chat"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 450, y = 0, width = 450, height = 200), bounds["todos"])
        assertEquals(ComposeWorkspaceWindowBounds(x = 0, y = 400, width = 450, height = 200), bounds["settings"])
        assertFalse(bounds.containsKey("canvas"))
    }

    private fun testWindow(
        id: String,
        visible: Boolean = true,
    ): ComposeWorkspaceWindow =
        ComposeWorkspaceWindow(
            id = id,
            icon = id.take(1),
            title = id,
            subtitle = id,
            bounds = ComposeWorkspaceWindowBounds(x = 0, y = 0, width = 100, height = 100),
            visible = visible,
        )
}
