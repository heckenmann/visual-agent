package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationContextWarningLifecycleTest {
    @Test
    fun `context reduction warning is cleared when the request finishes`() =
        runBlocking {
            val contextReduced = MutableStateFlow(false)
            val gateway =
                object : ConversationMessageGateway {
                    override suspend fun stream(
                        request: ConversationStreamRequest,
                        token: CancellationToken,
                        onChunk: (ConversationStreamUpdate) -> Unit,
                    ) {
                        onChunk(ConversationStreamUpdate("turn", "partial", contextReduced = true))
                        assertTrue(contextReduced.value)
                    }

                    override suspend fun currentHistory(): List<ConversationMessage> = emptyList()
                }

            executeSend(
                content = "question",
                messageGateway = gateway,
                inFlight = InFlightStateHolder(),
                onInputChange = {},
                onSendingChange = {},
                onStatusChange = {},
                onActiveTokenChange = {},
                onPendingUserMessageChange = {},
                onPendingUserEntryIdChange = {},
                onStreamingEntryIdChange = {},
                onStreamCompletion = {},
                streamingFlow = MutableStateFlow(""),
                contextReducedFlow = contextReduced,
            )

            assertFalse(contextReduced.value)
        }
}
