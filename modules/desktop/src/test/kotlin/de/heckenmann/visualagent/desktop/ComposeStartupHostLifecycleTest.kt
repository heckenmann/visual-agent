package de.heckenmann.visualagent.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import de.heckenmann.visualagent.protocol.ApplicationConnection
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.LayoutPosition
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import de.heckenmann.visualagent.ui.application.ComposeApplicationDependencies
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ConfigurableApplicationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Verifies that the desktop exits even when one shutdown component reports an error. */
class ComposeStartupHostLifecycleTest {
    @Test
    fun `exit callback is invoked when spring context close fails`() {
        val lifecycle = mockk<LifecyclePort>(relaxed = true)
        val layout = mockk<WorkspaceLayoutPort>(relaxed = true)
        val applicationPort = mockk<ApplicationPort>(relaxed = true)
        every { applicationPort.lifecycle } returns lifecycle
        every { applicationPort.layout } returns layout
        val context = mockk<ConfigurableApplicationContext>(relaxed = true)
        every { context.close() } throws IllegalStateException("context close failed")
        val connection = mockk<ApplicationConnection>(relaxed = true)
        var exited = false

        assertFailsWith<IllegalStateException> {
            closeApplication(
                dependencies = ComposeApplicationDependencies(applicationPort),
                windowState = WindowState(size = DpSize(800.dp, 600.dp)),
                connection = connection,
                context = context,
                exitApplication = { exited = true },
            )
        }

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
