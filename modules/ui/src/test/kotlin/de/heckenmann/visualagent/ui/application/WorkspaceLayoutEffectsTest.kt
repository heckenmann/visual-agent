package de.heckenmann.visualagent.ui.application

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** Verifies layout I/O isolation and ordered persistence while presentation changes continue. */
class WorkspaceLayoutEffectsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `recreated workspace continues persisting visibility order and widths`() {
        val port = mockk<WorkspaceLayoutPort>()
        val coordinator = WorkspaceLayoutPersistenceCoordinator(port)
        val initial = listOf(LayoutWindowState("chat"), LayoutWindowState("todos", order = 1))
        val changed = listOf(initial[1].copy(order = 0, visible = false, preferredWidth = 540.0), initial[0].copy(order = 1))
        val mounted = mutableStateOf(true)
        val windows = mutableStateOf(initial)
        val writes = CopyOnWriteArrayList<List<LayoutWindowState>>()
        every { port.bind(any(), any(), any()) } returns Unit
        every { port.applyWindowStates(any(), false) } answers { writes.add(firstArg()) }
        compose.setContent {
            if (mounted.value) {
                WorkspaceLayoutEffects(port, LayoutSize(1200.0, 800.0), LayoutSize(1000.0, 700.0), windows.value, coordinator)
            }
        }
        compose.waitUntil(10_000) { writes.contains(initial) }
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        // Await disposal's ordered flush before reusing the window-owned coordinator.
        runBlocking { coordinator.publishLatest() }
        compose.runOnIdle {
            windows.value = changed
            mounted.value = true
        }
        compose.waitForIdle()
        runBlocking { coordinator.publishLatest() }
        assertEquals(changed, writes.last())
    }

    @Test
    fun `finish waits for pending writes and prevents writes after server cleanup`(): Unit =
        runBlocking {
            val port = mockk<WorkspaceLayoutPort>()
            val coordinator = WorkspaceLayoutPersistenceCoordinator(port)
            val initial = listOf(LayoutWindowState("chat"), LayoutWindowState("todos", order = 1))
            val reordered = listOf(initial[1].copy(order = 0), initial[0].copy(order = 1))
            val started = CompletableDeferred<Unit>()
            val release = CompletableFuture<Unit>()
            val writes = CopyOnWriteArrayList<List<LayoutWindowState>>()
            every { port.bind(any(), any(), any()) } returns Unit
            every { port.applyWindowStates(any(), false) } answers {
                val state = firstArg<List<LayoutWindowState>>()
                if (state == initial) {
                    started.complete(Unit)
                    release.get(10, TimeUnit.SECONDS)
                }
                writes.add(state)
            }
            coordinator.update(LayoutSize(1200.0, 800.0), LayoutSize(1000.0, 700.0), initial)
            val writer = async(Dispatchers.IO) { coordinator.publishLatest() }
            try {
                started.await()
                coordinator.update(LayoutSize(1200.0, 800.0), LayoutSize(1000.0, 700.0), reordered)
                val finishStarted = CompletableDeferred<Unit>()
                val finish =
                    async {
                        finishStarted.complete(Unit)
                        coordinator.finish()
                    }
                finishStarted.await()
                assertFalse(finish.isCompleted)
                release.complete(Unit)
                writer.await()
                finish.await()
                assertEquals(listOf(initial, reordered), writes.toList())

                every { port.bind(any(), any(), any()) } throws IllegalStateException("Server closed")
                coordinator.update(LayoutSize(1200.0, 800.0), LayoutSize(1000.0, 700.0), initial)
                coordinator.publishLatest()
                coordinator.finish()
                assertEquals(listOf(initial, reordered), writes.toList())
            } finally {
                release.complete(Unit)
                writer.cancel()
                writer.join()
            }
        }

    @Test
    fun `removing workspace flushes its latest observed order after a blocked write`() {
        val port = mockk<WorkspaceLayoutPort>()
        val initial = listOf(LayoutWindowState("chat"), LayoutWindowState("todos", order = 1))
        val reordered = listOf(initial[1].copy(order = 0), initial[0].copy(order = 1))
        val windows = mutableStateOf(initial)
        val visible = mutableStateOf(true)
        val started = CompletableFuture<Unit>()
        val release = CompletableFuture<Unit>()
        val finished = CompletableFuture<Unit>()
        val writes = CopyOnWriteArrayList<List<LayoutWindowState>>()
        every { port.bind(any(), any(), any()) } returns Unit
        every { port.applyWindowStates(any(), false) } answers {
            val state = firstArg<List<LayoutWindowState>>()
            if (state == initial) {
                started.complete(Unit)
                release.get(10, TimeUnit.SECONDS)
            }
            writes.add(state)
            if (state == reordered) finished.complete(Unit)
        }
        try {
            compose.setContent {
                if (visible.value) {
                    WorkspaceLayoutEffects(port, LayoutSize(1200.0, 800.0), LayoutSize(1000.0, 700.0), windows.value)
                }
            }
            started.get(10, TimeUnit.SECONDS)
            compose.runOnIdle { windows.value = reordered }
            compose.waitForIdle()
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            assertEquals(emptyList(), writes.toList())
            release.complete(Unit)
            compose.waitUntil(timeoutMillis = 10_000) { finished.isDone }
            assertEquals(listOf(initial, reordered), writes.toList())
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun `blocked persistence leaves composition responsive and saves the latest panel order`() {
        val port = mockk<WorkspaceLayoutPort>()
        val initial = listOf(LayoutWindowState("chat"), LayoutWindowState("todos", order = 1))
        val reordered = listOf(initial[1].copy(order = 0), initial[0].copy(order = 1))
        val windows = mutableStateOf(initial)
        val desktop = mutableStateOf(LayoutSize(1000.0, 700.0))
        val started = CompletableFuture<Unit>()
        val release = CompletableFuture<Unit>()
        val finished = CompletableFuture<Unit>()
        val writes = CopyOnWriteArrayList<List<LayoutWindowState>>()
        var uiThread: Thread? = null
        compose.runOnIdle { uiThread = Thread.currentThread() }
        every { port.bind(any(), any(), any()) } answers {
            assertNotEquals(uiThread, Thread.currentThread())
        }
        every { port.applyWindowStates(any(), false) } answers {
            assertNotEquals(uiThread, Thread.currentThread())
            val state = firstArg<List<LayoutWindowState>>()
            if (state == initial) {
                started.complete(Unit)
                release.get(10, TimeUnit.SECONDS)
            }
            writes.add(state)
            if (state == reordered) finished.complete(Unit)
        }
        try {
            compose.setContent { WorkspaceLayoutEffects(port, LayoutSize(1200.0, 800.0), desktop.value, windows.value) }
            started.get(10, TimeUnit.SECONDS)
            compose.runOnIdle {
                windows.value = reordered
                desktop.value = LayoutSize(1100.0, 750.0)
            }
            compose.waitForIdle()
            assertEquals(emptyList(), writes.toList())
            release.complete(Unit)
            compose.waitUntil(timeoutMillis = 10_000) { finished.isDone }
            assertEquals(listOf(initial, reordered), writes.toList())
        } finally {
            release.complete(Unit)
        }
    }
}
