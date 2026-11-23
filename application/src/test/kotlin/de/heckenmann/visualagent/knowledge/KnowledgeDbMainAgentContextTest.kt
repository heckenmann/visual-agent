package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.flywaydb.core.Flyway
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class KnowledgeDbMainAgentContextTest {
    @Test
    fun `upgrading existing history adds the user turn index and preserves stored messages`() {
        val url = "jdbc:h2:mem:context-index-${UUID.randomUUID()}"
        val migrations = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration-h2")
        DriverManager.getConnection(url, "sa", "").use { connection ->
            migrations.target("7").load().migrate()
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO conversation_history (id, session_id, role, content) VALUES ('saved', 'main', 'user', 'Existing message')",
                )
                migrations.target("8").load().migrate()
                statement
                    .executeQuery(
                        "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = 'IDX_CONVERSATION_HISTORY_SESSION_ROLE'",
                    ).use { rows -> assertTrue(rows.next()) }
                statement.executeQuery("SELECT content FROM conversation_history WHERE id = 'saved'").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("Existing message", rows.getString(1))
                }
            }
        }
    }

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
