package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class KnowledgeDbMainAgentContextTest {
    @Test
    fun `main context query returns only the newest bounded dialogue rows`() {
        val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:bounded-main-context")
        repeat(12) { index ->
            db.conversationStore.saveConversationMessage(
                UUID.randomUUID().toString(),
                "main",
                "user",
                "Request $index",
                contextPolicy = ConversationContextPolicy.DIALOGUE,
            )
            db.conversationStore.saveConversationMessage(
                UUID.randomUUID().toString(),
                "main",
                "assistant",
                "Answer $index",
                contextPolicy = ConversationContextPolicy.DIALOGUE,
            )
        }

        val context = db.conversationStore.getConversationMessagesForContext("main", Int.MAX_VALUE, 4)

        assertEquals(listOf("Request 10", "Answer 10", "Request 11", "Answer 11"), context.map { it.content })
        assertEquals(2, context.count { it.role == "user" })
        assertTrue(context.none { it.content == "Request 0" || it.content == "Answer 0" })
        db.close()
    }
}
