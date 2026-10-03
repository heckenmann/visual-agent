package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.testsupport.TestPersistence
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals

/** Covers bulk reset integrity; diagnostic timings never determine test success. */
@DatabaseTest
class ConversationBulkResetTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `small and large grouped histories clear without deleting unrelated records`() {
        for (turns in listOf(10, 2_000)) {
            KnowledgeDbTestFactory.create(directory.resolve("history-$turns").toString()).use { db ->
                db.setPreference("keep", "preference")
                val skill = (db.skillStore.createSkill("Keep skill", "# Durable knowledge") as SkillCreateResult.Created).skill
                db.saveConversationMessage(UUID.randomUUID().toString(), "other", "user", "Keep other session")
                seed(db, turns)
                assertEquals(turns * 5, count(db, "main"))

                val baselineStart = System.nanoTime()
                db.databaseClient
                    .sql("DELETE FROM conversation_history WHERE session_id = 'main'")
                    .fetch()
                    .rowsUpdated()
                    .block()
                val baselineNanos = System.nanoTime() - baselineStart
                assertEquals(0, count(db, "main"))

                seed(db, turns)
                val resetStart = System.nanoTime()
                db.deleteConversationMessages("main")
                val resetNanos = System.nanoTime() - resetStart

                assertEquals(0, count(db, "main"))
                assertEquals(1, count(db, "other"))
                assertEquals("preference", db.getPreference("keep"))
                assertEquals(skill, db.skillStore.getSkill(skill.id))
                println(
                    "Conversation reset diagnostic: rows=${turns * 5}, bulkDeleteMs=${baselineNanos / 1_000_000.0}, guardedResetMs=${resetNanos / 1_000_000.0}",
                )
            }
        }
    }

    private fun count(
        db: TestPersistence,
        sessionId: String,
    ): Int =
        db.databaseClient
            .sql("SELECT COUNT(*) AS total FROM conversation_history WHERE session_id = :session")
            .bind("session", sessionId)
            .map { row, _ -> (row.get("total") as Number).toInt() }
            .one()
            .block()!!

    private fun seed(
        db: TestPersistence,
        turns: Int,
    ) {
        val requestId = UUID.randomUUID().toString()
        db.beginConversationRequest("main", requestId)
        db.databaseClient
            .sql(
                """
                INSERT INTO conversation_history
                    (id, session_id, role, content, timeline_sequence, assistant_tool_turn, conversation_request_id)
                SELECT CAST(RANDOM_UUID() AS VARCHAR), 'main', 'assistant', 'Inspect four files',
                       NEXT VALUE FOR visual_agent_timeline_sequence, TRUE, :request
                FROM SYSTEM_RANGE(1, :turns)
                """.trimIndent(),
            ).bind("request", requestId)
            .bind("turns", turns)
            .fetch()
            .rowsUpdated()
            .block()
        db.databaseClient
            .sql(
                """
                INSERT INTO conversation_history
                    (id, session_id, role, content, timeline_sequence, parent_assistant_turn_id, turn_order, conversation_request_id)
                SELECT CAST(RANDOM_UUID() AS VARCHAR), 'main', 'tool', REPEAT('Sample result ', 300),
                       NEXT VALUE FOR visual_agent_timeline_sequence, parent.id, tool.X - 1, :request
                FROM conversation_history parent CROSS JOIN SYSTEM_RANGE(1, 4) tool
                WHERE parent.session_id = 'main' AND parent.role = 'assistant'
                """.trimIndent(),
            ).bind("request", requestId)
            .fetch()
            .rowsUpdated()
            .block()
    }
}
