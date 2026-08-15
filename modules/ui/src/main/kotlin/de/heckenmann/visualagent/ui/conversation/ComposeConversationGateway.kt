@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface ConversationHistoryGateway {
    suspend fun latest(): ConversationHistoryPage

    suspend fun older(offset: Int): ConversationHistoryPage
}

internal interface ConversationMessageGateway {
    suspend fun stream(
        content: String,
        token: CancellationToken,
        onChunk: (String) -> Unit,
    )

    fun currentHistory(): List<ConversationMessage>
}

internal class ProtocolConversationGateway(
    private val conversationPort: ConversationPort,
) : ConversationHistoryGateway,
    ConversationMessageGateway {
    override suspend fun latest(): ConversationHistoryPage = withContext(Dispatchers.IO) { conversationPort.latest() }

    override suspend fun older(offset: Int): ConversationHistoryPage = withContext(Dispatchers.IO) { conversationPort.older(offset) }

    override suspend fun stream(
        content: String,
        token: CancellationToken,
        onChunk: (String) -> Unit,
    ) {
        withContext(Dispatchers.IO) { conversationPort.stream(content, token, onChunk) }
    }

    override fun currentHistory(): List<ConversationMessage> = conversationPort.currentHistory()
}
