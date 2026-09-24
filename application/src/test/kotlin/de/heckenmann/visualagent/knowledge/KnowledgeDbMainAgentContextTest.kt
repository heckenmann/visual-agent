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
    fun `main context query can load history beyond ten user turns`() {
        val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:unbounded-main-context")
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

        val context = db.conversationStore.getConversationMessagesForContext("main", Int.MAX_VALUE, Int.MAX_VALUE)

        assertEquals(12, context.count { it.role == "user" })
        assertEquals("Request 0", context.first { it.role == "user" }.content)
        assertTrue(context.any { it.content == "Answer 0" })
        assertTrue(context.any { it.content == "Answer 11" })
        db.close()
    }
}
