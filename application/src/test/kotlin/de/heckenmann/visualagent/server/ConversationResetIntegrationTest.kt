package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Sinks
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises persisted reset acknowledgement independently of provider latency. */
@DatabaseTest
class ConversationResetIntegrationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `small and large histories acknowledge reset before provider connection finishes`() =
        runBlocking {
            for (turns in listOf(10, 2_000)) {
                KnowledgeDbTestFactory.create(directory.resolve("reset-$turns").toString()).use { db ->
                    db.databaseClient
                        .sql(
                            """
                            INSERT INTO conversation_history (id, session_id, role, content, timeline_sequence, assistant_tool_turn)
                            SELECT CAST(RANDOM_UUID() AS VARCHAR), 'main', 'assistant', 'Old assistant turn',
                                   NEXT VALUE FOR visual_agent_timeline_sequence, TRUE FROM SYSTEM_RANGE(1, :turns)
                            """.trimIndent(),
                        ).bind("turns", turns)
                        .fetch()
                        .rowsUpdated()
                        .block()
                    db.databaseClient
                        .sql(
                            """
                            INSERT INTO conversation_history
                                (id, session_id, role, content, timeline_sequence, parent_assistant_turn_id, turn_order)
                            SELECT CAST(RANDOM_UUID() AS VARCHAR), 'main', 'tool', REPEAT('Sample result ', 300),
                                   NEXT VALUE FOR visual_agent_timeline_sequence, parent.id, tool.X - 1
                            FROM conversation_history parent CROSS JOIN SYSTEM_RANGE(1, 4) tool
                            WHERE parent.session_id = 'main' AND parent.role = 'assistant'
                            """.trimIndent(),
                        ).fetch()
                        .rowsUpdated()
                        .block()
                    val provider = mockk<LLMProvider>(relaxed = true)
                    val connection = Sinks.one<Boolean>()
                    val subscribed = CompletableDeferred<Unit>()
                    every { provider.checkConnectionReactive() } returns connection.asMono().doOnSubscribe { subscribed.complete(Unit) }
                    val config = AppConfigBean(db)
                    val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), config)
                    val port = SpringConversationPort(manager, config, mockk(relaxed = true))
                    val cleared = CompletableDeferred<Unit>()
                    val started = System.nanoTime()
                    var acknowledgementNanos = 0L
                    val operation =
                        async {
                            port.clearAndCreateWelcome {
                                acknowledgementNanos = System.nanoTime() - started
                                cleared.complete(Unit)
                            }
                        }
                    try {
                        cleared.await()
                        subscribed.await()
                        assertFalse(operation.isCompleted)
                        assertTrue(port.latest().messages.isEmpty())
                        assertTrue(db.getConversationMessages("main", 20_000).isEmpty())
                        val refreshedNanos = System.nanoTime() - started
                        println(
                            "Reset protocol diagnostic: rows=${turns * 5}, acknowledgementMs=${acknowledgementNanos / 1_000_000.0}, refreshedMs=${refreshedNanos / 1_000_000.0}",
                        )
                        connection.tryEmitValue(false)
                        assertNotNull(operation.await().warning)
                        assertEquals(1, db.getConversationMessages("main", 20_000).size)
                    } finally {
                        connection.tryEmitValue(false)
                        operation.cancel()
                        operation.join()
                        manager.destroy()
                    }
                }
            }
        }
}
