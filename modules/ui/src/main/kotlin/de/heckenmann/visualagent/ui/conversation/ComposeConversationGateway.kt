@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage
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

    fun currentHistory(): List<Message>
}

internal class AgentManagerConversationGateway(
    private val agentManager: AgentManager,
) : ConversationHistoryGateway,
    ConversationMessageGateway {
    override suspend fun latest(): ConversationHistoryPage = withContext(Dispatchers.IO) { agentManager.readLatestHistoryPage() }

    override suspend fun older(offset: Int): ConversationHistoryPage =
        withContext(Dispatchers.IO) { agentManager.readOlderHistoryPage(offset) }

    override suspend fun stream(
        content: String,
        token: CancellationToken,
        onChunk: (String) -> Unit,
    ) {
        withContext(Dispatchers.IO) { agentManager.streamMessage(content, token, onChunk) }
    }

    override fun currentHistory(): List<Message> = agentManager.getHistory()
}
