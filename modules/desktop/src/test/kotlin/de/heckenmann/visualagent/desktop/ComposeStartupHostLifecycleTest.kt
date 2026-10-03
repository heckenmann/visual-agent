package de.heckenmann.visualagent.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.LayoutPosition
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.protocol.OnboardingState
import de.heckenmann.visualagent.protocol.OnboardingStatus
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import de.heckenmann.visualagent.protocol.WorkspaceLayoutSnapshot
import de.heckenmann.visualagent.ui.application.ComposeApplicationDependencies
import de.heckenmann.visualagent.ui.application.StartupStatus
import de.heckenmann.visualagent.ui.application.WorkspaceLayoutPersistenceCoordinator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Verifies that desktop shutdown hands context disposal to the Compose lifecycle. */
class ComposeStartupHostLifecycleTest {
    @Test
    fun `resource cleanup leaves the caller responsive and is awaited before process completion`() {
        val coordinator = DesktopShutdownCoordinator()
        val caller = Thread.currentThread()
        val entered = CompletableFuture<Unit>()
        val release = CompletableFuture<Unit>()
        var completed = false
        coordinator.closeResources {
            assertNotEquals(caller, Thread.currentThread())
            entered.complete(Unit)
            release.get(10, TimeUnit.SECONDS)
            completed = true
        }
        try {
            entered.get(10, TimeUnit.SECONDS)
            assertTrue(!completed)
        } finally {
            release.complete(Unit)
            coordinator.awaitResourceCleanup()
        }
        assertTrue(completed)
    }

    @Test
    fun `shutdown flushes layout off the caller thread before closing the application`() =
        runBlocking {
            val caller = Thread.currentThread()
            val applicationPort = mockk<ApplicationPort>(relaxed = true)
            val layout = mockk<WorkspaceLayoutPort>(relaxed = true)
            val lifecycle = mockk<LifecyclePort>(relaxed = true)
            every { applicationPort.layout } returns layout
            every { applicationPort.lifecycle } returns lifecycle
            every { layout.bind(any(), any(), any()) } answers { assertNotEquals(caller, Thread.currentThread()) }
            every { lifecycle.beginShutdown() } answers { assertNotEquals(caller, Thread.currentThread()) }
            every { applicationPort.cancelActiveWork() } answers { assertNotEquals(caller, Thread.currentThread()) }
            every { layout.saveStage(any(), any()) } answers { assertNotEquals(caller, Thread.currentThread()) }
            val persistence = WorkspaceLayoutPersistenceCoordinator(layout)
            val size = LayoutSize(800.0, 600.0)
            persistence.update(size, size, emptyList())
            val shutdown = DesktopShutdownCoordinator()
            var exits = 0
            repeat(2) {
                closeApplication(
                    ComposeApplicationDependencies(applicationPort),
                    WindowState(size = DpSize(800.dp, 600.dp)),
                    {
                        assertEquals(caller, Thread.currentThread())
                        exits += 1
                    },
                    shutdown,
                    persistence,
                )
            }
            assertEquals(1, exits)
            verifyOrder {
                layout.bind(size, size, emptyList())
                layout.applyWindowStates(emptyList(), false)
                lifecycle.beginShutdown()
                applicationPort.cancelActiveWork()
                layout.saveStage(size, any())
            }
        }

    @Test
    fun `exit callback is invoked before spring context disposal`() =
        runBlocking {
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
        assertEquals(
            StartupWindowMode.ONBOARDING,
            startupWindowMode(StartupStatus.ready(), dependencies, OnboardingState(OnboardingStatus.NOT_STARTED, 1)),
        )
        assertEquals(
            StartupWindowMode.ONBOARDING,
            startupWindowMode(StartupStatus.ready(), dependencies, manualOnboardingRequested = true),
        )
    }

    @Test
    fun `shutdown coordinator claims exit and closes resources exactly once`() {
        val coordinator = DesktopShutdownCoordinator()
        var closeCount = 0

        assertTrue(coordinator.requestExit())
        assertTrue(!coordinator.requestExit())
        coordinator.closeResources { closeCount += 1 }
        coordinator.closeResources { closeCount += 1 }
        coordinator.awaitResourceCleanup()

        assertEquals(1, closeCount)
    }

    @Test
    fun `smoke startup opens the workspace but keeps manual onboarding available`() {
        val dependencies = ComposeApplicationDependencies(mockk(relaxed = true))
        val onboarding = OnboardingState(OnboardingStatus.NOT_STARTED, 1)

        assertEquals(
            StartupWindowMode.MAIN,
            startupWindowMode(StartupStatus.ready(), dependencies, onboarding, skipAutomaticOnboarding = true),
        )
        assertEquals(
            StartupWindowMode.ONBOARDING,
            startupWindowMode(StartupStatus.ready(), dependencies, onboarding, true, skipAutomaticOnboarding = true),
        )
        assertEquals(
            StartupWindowMode.SPLASH,
            startupWindowMode(StartupStatus.startingServer(), dependencies, onboarding, skipAutomaticOnboarding = true),
        )
    }
}
