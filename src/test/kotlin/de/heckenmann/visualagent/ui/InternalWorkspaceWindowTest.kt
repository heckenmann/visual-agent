package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.ui.panels.FxTestSupport
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Pane
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InternalWorkspaceWindowTest {
    @Test
    fun `workspace manager registers panels as active internal windows`() =
        FxTestSupport.run {
            val desktop = Pane()
            desktop.resize(900.0, 700.0)
            val panel = Label("Panel")
            val manager = WorkspaceWindowManager(desktop)

            manager.register("panel", "Panel", "fas-copy", panel, WindowPlacement(12.0, 18.0, 420.0, 320.0))
            assertTrue(manager.windowFor(panel)?.isVisible == false)
            manager.focus(panel)

            val window = manager.windowFor(panel)
            assertEquals(1, desktop.children.size)
            assertSame(window, desktop.children.single())
            assertSame(panel, window?.center)
            assertEquals(12.0, window?.layoutX)
            assertEquals(18.0, window?.layoutY)
            assertEquals(420.0, window?.prefWidth)
            assertEquals(320.0, window?.prefHeight)
            assertTrue(window?.isVisible == true)
            assertTrue(window?.styleClass?.contains("workspace-window-active") == true)
        }

    @Test
    fun `focused window is clamped into the visible desktop`() =
        FxTestSupport.run {
            val desktop = Pane()
            desktop.resize(500.0, 360.0)
            val panel = Label("Panel")
            val manager = WorkspaceWindowManager(desktop)

            manager.register("panel", "Panel", "fas-copy", panel, WindowPlacement(420.0, 320.0, 260.0, 220.0))
            manager.focus(panel)

            val window = manager.windowFor(panel)
            assertEquals(80.0, window?.layoutX)
            assertEquals(40.0, window?.layoutY)
            assertEquals(420.0, window?.prefWidth)
            assertEquals(320.0, window?.prefHeight)
        }

    @Test
    fun `workspace window refuses sizes below content-safe minimum`() =
        FxTestSupport.run {
            val window = InternalWorkspaceWindow("Panel", "fas-copy", Label("Panel"))

            window.place(0.0, 0.0, 120.0, 90.0)

            assertEquals(420.0, window.prefWidth)
            assertEquals(320.0, window.prefHeight)
        }

    @Test
    fun `workspace manager restores persisted window layout`() =
        FxTestSupport.run {
            val desktop = Pane()
            desktop.resize(900.0, 700.0)
            val panel = Label("Panel")
            val manager = WorkspaceWindowManager(desktop)

            manager.register("panel", "Panel", "fas-copy", panel, WindowPlacement(12.0, 18.0, 420.0, 320.0))
            manager.restore(
                WorkspaceLayout(
                    listOf(
                        WorkspaceWindowState(
                            id = "panel",
                            x = 44.0,
                            y = 55.0,
                            width = 500.0,
                            height = 360.0,
                            visible = true,
                            zIndex = 2,
                        ),
                    ),
                ),
            )

            val window = manager.windowFor(panel)
            assertEquals(44.0, window?.layoutX)
            assertEquals(55.0, window?.layoutY)
            assertEquals(500.0, window?.prefWidth)
            assertEquals(360.0, window?.prefHeight)
            assertTrue(window?.isVisible == true)
        }

    @Test
    fun `workspace manager clamps restored windows into smaller desktop`() =
        FxTestSupport.run {
            val desktop = Pane()
            desktop.resize(500.0, 360.0)
            val panel = Label("Panel")
            val manager = WorkspaceWindowManager(desktop)

            manager.register("panel", "Panel", "fas-copy", panel, WindowPlacement(12.0, 18.0, 420.0, 320.0))
            manager.restore(
                WorkspaceLayout(
                    listOf(
                        WorkspaceWindowState(
                            id = "panel",
                            x = 900.0,
                            y = 650.0,
                            width = 760.0,
                            height = 520.0,
                            visible = true,
                            zIndex = 2,
                        ),
                    ),
                ),
            )

            val window = manager.windowFor(panel)
            assertEquals(0.0, window?.layoutX)
            assertEquals(0.0, window?.layoutY)
            assertEquals(500.0, window?.prefWidth)
            assertEquals(360.0, window?.prefHeight)
            assertTrue(window?.isVisible == true)
        }

    @Test
    fun `workspace manager keeps restored hidden windows reachable after later focus`() =
        FxTestSupport.run {
            val desktop = Pane()
            desktop.resize(500.0, 360.0)
            val panel = Label("Panel")
            val manager = WorkspaceWindowManager(desktop)

            manager.register("panel", "Panel", "fas-copy", panel, WindowPlacement(12.0, 18.0, 420.0, 320.0))
            manager.restore(
                WorkspaceLayout(
                    listOf(
                        WorkspaceWindowState(
                            id = "panel",
                            x = 900.0,
                            y = 650.0,
                            width = 420.0,
                            height = 320.0,
                            visible = false,
                            zIndex = 2,
                        ),
                    ),
                ),
            )
            manager.focus(panel)

            val window = manager.windowFor(panel)
            assertEquals(80.0, window?.layoutX)
            assertEquals(40.0, window?.layoutY)
            assertTrue(window?.isVisible == true)
        }

    @Test
    fun `workspace manager snapshots visibility position size and z order`() =
        FxTestSupport.run {
            val desktop = Pane()
            desktop.resize(900.0, 700.0)
            val first = Label("First")
            val second = Label("Second")
            val manager = WorkspaceWindowManager(desktop)

            manager.register("first", "First", "fas-copy", first, WindowPlacement(1.0, 2.0, 300.0, 240.0))
            manager.register("second", "Second", "fas-copy", second, WindowPlacement(3.0, 4.0, 320.0, 260.0))
            manager.focus(first)
            manager.focus(second)

            val layout = manager.snapshot()
            val firstState = layout.windows.first { it.id == "first" }
            val secondState = layout.windows.first { it.id == "second" }
            assertTrue(firstState.visible)
            assertTrue(secondState.visible)
            assertTrue(secondState.zIndex > firstState.zIndex)
            assertEquals(3.0, secondState.x)
            assertEquals(4.0, secondState.y)
        }

    @Test
    fun `close button hides the internal window without removing the hosted panel`() =
        FxTestSupport.run {
            val panel = Label("Panel")
            val window = InternalWorkspaceWindow("Panel", "fas-copy", panel)
            window.isVisible = true
            window.isManaged = true

            val closeButton = window.lookup(".workspace-window-close") as Button
            closeButton.fire()

            assertTrue(!window.isVisible)
            assertTrue(!window.isManaged)
            assertSame(panel, window.center)
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
