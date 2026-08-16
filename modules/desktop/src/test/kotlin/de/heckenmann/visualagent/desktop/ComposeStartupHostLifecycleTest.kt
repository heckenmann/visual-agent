package de.heckenmann.visualagent.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.LayoutPosition
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import de.heckenmann.visualagent.ui.application.ComposeApplicationDependencies
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
}
