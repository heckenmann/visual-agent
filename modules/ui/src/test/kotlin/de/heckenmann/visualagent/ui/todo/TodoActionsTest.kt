package de.heckenmann.visualagent.ui.todo

import de.heckenmann.visualagent.ui.modal.ComposeInfoModal
import de.heckenmann.visualagent.ui.modal.ComposeModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests the action queue without depending on Compose animation timing. */
class TodoActionsTest {
    @Test
    fun `slow mutation leaves UI responsive and preserves action order`() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                withTimeout(5000) {
                    val uiThread = Thread.currentThread()
                    val started = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    val responsive = CompletableDeferred<Unit>()
                    val successes = Channel<Unit>(Channel.UNLIMITED)
                    val calls = CopyOnWriteArrayList<String>()
                    val actions = TodoActions(this, ComposeModalRequester { error("Unexpected failure") })
                    actions.submit("first", {
                        assertFalse(Thread.currentThread() === uiThread)
                        started.complete(Unit)
                        runBlocking { release.await() }
                        calls += "first"
                    }, { successes.trySend(Unit) })
                    actions.submit("first", { calls += "duplicate" })
                    actions.submit("second", { calls += "second" }, { successes.trySend(Unit) })
                    started.await()
                    launch { responsive.complete(Unit) }
                    responsive.await()
                    assertTrue(calls.isEmpty())
                    release.complete(Unit)
                    repeat(2) { successes.receive() }
                    assertEquals(listOf("first", "second"), calls)
                }
            }
        }
    }

    @Test
    fun `failure is displayed and does not invoke success callback`() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                withTimeout(5000) {
                    val failure = CompletableDeferred<ComposeModal>()
                    var succeeded = false
                    val actions = TodoActions(this, ComposeModalRequester { failure.complete(it) })
                    actions.submit("edit", { error("Conflicting edit") }, { succeeded = true })
                    val modal = failure.await() as ComposeInfoModal
                    assertEquals("Todo action failed", modal.title)
                    assertEquals("Conflicting edit", modal.message)
                    assertFalse(succeeded)
                }
            }
        }
    }
}
