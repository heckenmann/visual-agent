package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies the server-side assistant completion event stream and listener adapter. */
class ConversationCompletionEventBusTest {
    @Test
    fun `completion bus notifies active listeners and does not replay after close`() {
        val bus = ConversationCompletionEventBus()
        val received = mutableListOf<ConversationCompletionEvent>()
        val registration = bus.addListener(received::add)
        val event = ConversationCompletionEvent(ASSISTANT_ID, 17)

        bus.publish(event)
        registration.close()
        bus.publish(ConversationCompletionEvent(ASSISTANT_ID, 18))

        assertEquals(listOf(event), received)
    }

    private companion object {
        const val ASSISTANT_ID = "22222222-2222-4222-8222-222222222222"
    }
}
