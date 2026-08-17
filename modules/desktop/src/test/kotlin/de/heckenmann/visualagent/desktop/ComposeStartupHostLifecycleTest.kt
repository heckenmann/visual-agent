package de.heckenmann.visualagent.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.LayoutPosition
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import de.heckenmann.visualagent.protocol.WorkspaceLayoutSnapshot
import de.heckenmann.visualagent.ui.application.ComposeApplicationDependencies
import de.heckenmann.visualagent.ui.application.StartupStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies that desktop shutdown hands context disposal to the Compose lifecycle. */
class ComposeStartupHostLifecycleTest {
    @Test
    fun `exit callback is invoked before spring context disposal`() {
        val lifecycle = mockk<LifecyclePort>(relaxed = true)
        val layout = mockk<WorkspaceLayoutPort>(relaxed = true)
        val applicationPort = mockk<ApplicationPort>(relaxed = true)
        every { applicationPort.lifecycle } returns lifecycle
        every { applicationPort.layout } returns layout
        var exited = false

        closeApplication(
            dependencies = ComposeApplicationDependencies(applicationPort),
            windowState = WindowState(size = DpSize(800.dp, 600.dp)),
            exitApplication = { exited = true },
        )

        assertTrue(exited)
        verify { lifecycle.beginShutdown() }
        verify { applicationPort.cancelActiveWork() }
    }

    @Test
    fun `restored window position is clamped to the current screen`() {
        val restored =
            clampWindowPosition(
                position = LayoutPosition(x = -500.0, y = 900.0),
                windowSize = LayoutSize(width = 800.0, height = 600.0),
                screenBounds = ScreenBounds(x = 0.0, y = 0.0, width = 1920.0, height = 1080.0),
            )

        assertEquals(LayoutPosition(x = 0.0, y = 480.0), restored)
    }

    @Test
    fun `main geometry restoration keeps persisted size and clamps position`() {
        val state = WindowState(size = DpSize(640.dp, 480.dp))

        restoreMainWindowGeometry(
            windowState = state,
            persistedLayout =
                WorkspaceLayoutSnapshot(
                    stage = LayoutSize(width = 800.0, height = 600.0),
                    stagePosition = LayoutPosition(x = 1800.0, y = 900.0),
                ),
            screenBounds = ScreenBounds(x = 0.0, y = 0.0, width = 1920.0, height = 1080.0),
        )

        assertEquals(DpSize(800.dp, 600.dp), state.size)
        assertEquals(1120.dp, state.position.x)
        assertEquals(480.dp, state.position.y)
    }

    @Test
    fun `startup creates only the splash until runtime and dependencies are ready`() {
        val dependencies = ComposeApplicationDependencies(mockk(relaxed = true))

        assertEquals(StartupWindowMode.SPLASH, startupWindowMode(StartupStatus.startingServer(), dependencies))
        assertEquals(StartupWindowMode.SPLASH, startupWindowMode(StartupStatus.failed(), dependencies))
        assertEquals(StartupWindowMode.MAIN, startupWindowMode(StartupStatus.ready(), dependencies))
    }

    @Test
    fun `shutdown coordinator claims exit and closes resources exactly once`() {
        val coordinator = DesktopShutdownCoordinator()
        var closeCount = 0

        assertTrue(coordinator.requestExit())
        assertTrue(!coordinator.requestExit())
        coordinator.closeResources { closeCount += 1 }
        coordinator.closeResources { closeCount += 1 }

        assertEquals(1, closeCount)
    }
}
