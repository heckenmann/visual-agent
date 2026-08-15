package de.heckenmann.visualagent.protocol

import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies the transport-neutral cancellation behavior used by conversation ports. */
class ConversationPortTest {
    @Test
    fun `cancellation is idempotent and notifies listeners`() {
        val token = CancellationTokenImpl()
        var notifications = 0
        token.onCancelled { notifications += 1 }

        assertFalse(token.isCancelled)
        token.cancel()
        token.cancel()

        assertTrue(token.isCancelled)
        assertEquals(1, notifications)
    }

    @Test
    fun `listener registered after cancellation is invoked immediately`() {
        val token = CancellationTokenImpl()
        token.cancel()
        var notified = false

        token.onCancelled { notified = true }

        assertTrue(notified)
    }
}
