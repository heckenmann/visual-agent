package de.heckenmann.visualagent.desktop

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.skiko.GraphicsApi
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Exercises startup heartbeats and observation lifetime independently of wall-clock time. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupRenderDiagnosticsTest {
    @Test
    fun `native backend exhaustion is recognized even through a startup wrapper`() {
        val renderException = Class.forName("org.jetbrains.skiko.RenderException").getConstructor()
        val failBackend: (GraphicsApi, Any?) -> Any = { _, _ -> throw renderException.newInstance() as Throwable }
        val manager = Class.forName("org.jetbrains.skiko.redrawer.RedrawerManager")
        val instance =
            manager
                .getConstructor(GraphicsApi::class.java, Function2::class.java, Function1::class.java)
                .newInstance(GraphicsApi.SOFTWARE_COMPAT, failBackend, null)

        val failure =
            assertFailsWith<InvocationTargetException> {
                manager.getMethod("findNextWorkingRenderApi", Boolean::class.javaPrimitiveType).invoke(instance, false)
            }
        assertSame(failure.targetException, nativeStartupRenderFailure(IllegalStateException(failure)))
        assertNull(nativeStartupRenderFailure(renderException.newInstance() as Throwable))
    }

    @Test
    fun `a responsive splash stays healthy during slow server startup`() =
        runTest {
            val events = mutableListOf<String>()
            val splash = StartupRenderDiagnostics(StartupWindowMode.SPLASH, events::add)
            splash.start()
            splash.backendChanged("OPENGL")
            splash.backendChanged("OPENGL")
            backgroundScope.launch { splash.watch(dispatchToUi = Runnable::run) }
            advanceTimeBy(60_000)
            assertEquals(
                listOf(
                    "window=SPLASH observing visualIntegrity=unknown",
                    "window=SPLASH backend=OPENGL previousBackend=unknown",
                ),
                events,
            )
        }

    @Test
    fun `render errors and backend transitions are distinct from other window errors`() {
        val events = mutableListOf<String>()
        val onboarding = StartupRenderDiagnostics(StartupWindowMode.ONBOARDING, events::add)
        onboarding.start()
        onboarding.backendChanged("OPENGL")
        val injected = IllegalStateException("sensitive details")
        injected.stackTrace = arrayOf(StackTraceElement("androidx.compose.ui.scene.ComposeSceneMediator", "onRender", null, -1))
        onboarding.exceptionReported(injected)
        onboarding.backendChanged("SOFTWARE")
        onboarding.exceptionReported(IllegalArgumentException())
        assertEquals(
            listOf(
                "window=ONBOARDING observing visualIntegrity=unknown",
                "window=ONBOARDING backend=OPENGL previousBackend=unknown",
                "window=ONBOARDING render-error cause=java.lang.IllegalStateException",
                "window=ONBOARDING backend=SOFTWARE previousBackend=OPENGL",
                "window=ONBOARDING window-error cause=java.lang.IllegalArgumentException",
            ),
            events,
        )
    }

    @Test
    fun `a blocked UI is reported once and only one heartbeat waits for it`() =
        runTest {
            val events = mutableListOf<String>()
            val queue = ArrayDeque<Runnable>()
            val splash = StartupRenderDiagnostics(StartupWindowMode.SPLASH, events::add)
            splash.start()
            backgroundScope.launch { splash.watch(dispatchToUi = queue::addLast) }
            advanceTimeBy(4_999)
            assertEquals(1, events.size)
            advanceTimeBy(30_000)
            assertEquals(1, queue.size)
            assertEquals("window=SPLASH ui-unresponsive timeoutMs=5000", events.last())
            queue.removeFirst().run()
            runCurrent()
            assertEquals("window=SPLASH ui-responsive", events.last())
        }

    @Test
    fun `a window transition cancels the old heartbeat and main observation expires`() =
        runTest {
            val events = mutableListOf<String>()
            val queue = ArrayDeque<Runnable>()
            val splash = StartupRenderDiagnostics(StartupWindowMode.SPLASH, events::add)
            splash.start()
            val splashWatchdog = backgroundScope.launch { splash.watch(dispatchToUi = queue::addLast) }
            runCurrent()
            splash.stop()
            splashWatchdog.cancel()
            queue.removeFirst().run()
            splash.backendChanged("SOFTWARE")
            val main = StartupRenderDiagnostics(StartupWindowMode.MAIN, events::add)
            main.start()
            val mainWatchdog = backgroundScope.launch { main.watch(dispatchToUi = Runnable::run) }
            advanceTimeBy(16_000)
            assertTrue(mainWatchdog.isCompleted)
            main.exceptionReported(IllegalStateException())
            assertEquals(
                listOf(
                    "window=SPLASH observing visualIntegrity=unknown",
                    "window=MAIN observing visualIntegrity=unknown",
                ),
                events,
            )
        }
}
