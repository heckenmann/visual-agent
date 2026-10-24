package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.ProviderReplayState
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DatabaseTest
class ConversationProviderReplayTest {
    @Test
    fun `native replay is retained by direct context history and paging queries`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
            val id = UUID.randomUUID().toString()
            val replay = replay()
            save(db.conversationStore, id, replay)

            assertEquals(replay, db.conversationStore.getConversationMessage(id)?.providerReplay)
            assertEquals(
                replay,
                db.conversationStore
                    .getConversationMessages("main")
                    .single()
                    .providerReplay,
            )
            assertEquals(
                replay,
                db.conversationStore
                    .getConversationMessagesPage("main", 20, 0)
                    .single()
                    .providerReplay,
            )
            assertEquals(
                replay,
                db.conversationStore
                    .getConversationMessagesForContext("main", 10, 20)
                    .single()
                    .providerReplay,
            )
            assertEquals(
                replay,
                db.conversationStore
                    .getLatestConversationHistoryPage("main", 20)
                    .records
                    .single()
                    .providerReplay,
            )
        }
    }

    @Test
    fun `editing answer text invalidates native replay through either update path`() {
        val edits: List<(ConversationStore, String) -> Int> =
            listOf(
                { store, id -> store.updateConversationMessageContent(id, "edited answer") },
                { store, id -> store.updateConversationMessage(id, "edited answer", "{}") },
            )
        edits.forEach { edit ->
            KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
                val id = UUID.randomUUID().toString()
                save(db.conversationStore, id, replay())

                assertEquals(1, edit(db.conversationStore, id))

                val stored = db.conversationStore.getConversationMessage(id)
                assertEquals("edited answer", stored?.content)
                assertNull(stored?.providerReplay)
            }
        }
    }

    @Test
    fun `metadata updates and unchanged text retain native replay`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
            val id = UUID.randomUUID().toString()
            save(db.conversationStore, id, replay())

            assertEquals(1, db.conversationStore.updateConversationMessageContent(id, "visible answer"))
            assertEquals(replay(), db.conversationStore.getConversationMessage(id)?.providerReplay)
            assertEquals(1, db.conversationStore.updateConversationMessage(id, "visible answer", "{}"))
            assertEquals(replay(), db.conversationStore.getConversationMessage(id)?.providerReplay)
        }
    }

    @Test
    fun `replay cannot mutate the identity of an already persisted assistant turn`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:test").use { db ->
            val id = UUID.randomUUID().toString()
            save(db.conversationStore, id, replay())

            assertFailsWith<IllegalArgumentException> { save(db.conversationStore, id, replay().copy(partsJson = "[]")) }

            assertEquals(replay(), db.conversationStore.getConversationMessage(id)?.providerReplay)
        }
    }

    private fun save(
        store: ConversationStore,
        id: String,
        state: ProviderReplayState,
    ) = store.saveConversationMessage(
        id,
        "main",
        "assistant",
        "visible answer",
        null,
        ConversationContextPolicy.SUMMARY_SOURCE,
        providerReplay = state,
    )

    private fun replay() =
        ProviderReplayState(
            "openai.responses.parts.v1",
            "profile",
            "model",
            """[{"type":"text","text":"visible answer"}]""",
        )
}
