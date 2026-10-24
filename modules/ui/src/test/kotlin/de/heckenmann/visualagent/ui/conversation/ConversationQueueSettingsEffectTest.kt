package de.heckenmann.visualagent.ui.conversation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Queue settings update without remounting input or waiting for stale initial reads. */
class ConversationQueueSettingsEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a newer setting event wins over a pending read and queued messages survive`() {
        val port = mockk<SettingsPort>()
        val initial = CompletableDeferred<SettingsSnapshot>()
        var listener: ((SettingsSnapshot) -> Unit)? = null
        coEvery { port.snapshotAsync() } coAnswers { initial.await() }
        every { port.addChangeListener(any()) } answers {
            listener = firstArg()
            AutoCloseable {}
        }
        val queue = MessageQueue()
        queue.enqueue("Retain", QueuedMessageSource.USER)
        compose.setContent { ConversationQueueSettingsEffect(port, queue) }
        compose.runOnIdle { requireNotNull(listener)(SettingsSnapshot(queueFlushMode = "ALL_AT_ONCE")) }
        compose.runOnIdle { assertEquals(QueueFlushMode.ALL_AT_ONCE, queue.flushMode) }
        compose.runOnIdle { initial.complete(SettingsSnapshot(queueFlushMode = "ONE_BY_ONE")) }
        compose.runOnIdle {
            assertEquals(QueueFlushMode.ALL_AT_ONCE, queue.flushMode)
            assertEquals("Retain", queue.messages.single().content)
            requireNotNull(listener)(SettingsSnapshot(queueFlushMode = "future"))
        }
        compose.runOnIdle { assertEquals(QueueFlushMode.ONE_BY_ONE, queue.flushMode) }
    }
}
