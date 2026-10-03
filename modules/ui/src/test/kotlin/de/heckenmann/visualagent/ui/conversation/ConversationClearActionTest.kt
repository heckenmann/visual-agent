package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ConversationClearResult
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies immediate reset feedback independently of welcome-provider completion. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationClearActionTest {
    @Test
    fun `cancelled reset releases the guard without reporting a deletion failure`() =
        runTest {
            val port = mockk<ConversationPort>()
            val finished = CompletableDeferred<Unit>()
            val guard = Mutex()
            var sending = false
            var status = ""
            every { port.cancelActiveWork() } returns Unit
            coEvery { port.clearAndCreateWelcome(any()) } throws CancellationException("Reset cancelled")
            handleClearConversation(
                scope = this,
                modalRequester = ComposeModalRequester { (it as ComposeConfirmationModal).onConfirm() },
                conversationPort = port,
                activeToken = { null },
                onSendingChange = {
                    sending = it
                    if (!it) finished.complete(Unit)
                },
                onStatusChange = { status = it },
                onHistoryRefresh = { error("Cancelled deletion must not refresh history") },
                onTodosCleared = { error("Cancelled deletion must not clear visible todos") },
                clearGuard = guard,
            )
            finished.await()
            assertEquals(false, sending)
            assertEquals("Stopping active work and clearing conversation...", status)
            assertTrue(guard.tryLock())
            guard.unlock()
        }

    @Test
    fun `cleared history is displayed while welcome remains pending`() =
        runTest {
            val port = mockk<ConversationPort>()
            val cleared = CompletableDeferred<Unit>()
            val releaseWelcome = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            var refreshes = 0
            var todosCleared = false
            var sending = false
            var status = ""
            val clearGuard = Mutex()
            every { port.cancelActiveWork() } returns Unit
            coEvery { port.clearAndCreateWelcome(any()) } coAnswers {
                firstArg<suspend () -> Unit>().invoke()
                cleared.complete(Unit)
                releaseWelcome.await()
                ConversationClearResult()
            }

            fun clear() =
                handleClearConversation(
                    scope = this,
                    modalRequester = ComposeModalRequester { (it as ComposeConfirmationModal).onConfirm() },
                    conversationPort = port,
                    activeToken = { null },
                    onSendingChange = {
                        sending = it
                        if (!it) finished.complete(Unit)
                    },
                    onStatusChange = { status = it },
                    onHistoryRefresh = { refreshes += 1 },
                    onTodosCleared = { todosCleared = true },
                    clearGuard = clearGuard,
                )
            clear()
            cleared.await()
            assertTrue(sending)
            assertTrue(todosCleared)
            assertEquals(1, refreshes)
            assertEquals("Conversation cleared. Preparing welcome message...", status)
            clear()
            runCurrent()
            coVerify(exactly = 1) { port.clearAndCreateWelcome(any()) }

            releaseWelcome.complete(Unit)
            finished.await()
            assertEquals(false, sending)
            assertEquals(2, refreshes)
            assertEquals("Conversation cleared", status)
        }

    @Test
    fun `deletion failure restores controls and does not display an empty history`() =
        runTest {
            val port = mockk<ConversationPort>()
            val finished = CompletableDeferred<Unit>()
            var refreshes = 0
            var status = ""
            every { port.cancelActiveWork() } returns Unit
            coEvery { port.clearAndCreateWelcome(any()) } throws IllegalStateException("Database unavailable")

            handleClearConversation(
                scope = this,
                modalRequester = ComposeModalRequester { (it as ComposeConfirmationModal).onConfirm() },
                conversationPort = port,
                activeToken = { null },
                onSendingChange = { if (!it) finished.complete(Unit) },
                onStatusChange = { status = it },
                onHistoryRefresh = { refreshes += 1 },
                onTodosCleared = { error("Todos must remain visible when deletion fails") },
                clearGuard = Mutex(),
            )
            finished.await()
            assertEquals(0, refreshes)
            assertEquals("Conversation could not be cleared: Database unavailable", status)
        }
}
