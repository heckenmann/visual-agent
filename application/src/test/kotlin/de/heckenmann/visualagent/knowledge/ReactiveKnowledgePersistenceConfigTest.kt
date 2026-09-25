package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the application-owned H2 R2DBC schema and file-backed restart behavior. */
class ReactiveKnowledgePersistenceConfigTest {
    @Test
    fun `Flyway initializes the reactive database and preserves data across restart`() {
        val databasePath = Files.createTempDirectory("visual-agent-r2dbc").resolve("database")
        val db = KnowledgeDbTestFactory.create(databasePath.toString())

        db.databaseClient
            .sql(
                "INSERT INTO user_preferences (preference_key, preference_value, preference_type, updated_at) VALUES ('probe', 'persisted', 'string', '2026-01-01T00:00:00Z')",
            ).fetch()
            .rowsUpdated()
            .block()
        val tableCount =
            db.databaseClient
                .sql("SELECT COUNT(*) AS count FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'USER_PREFERENCES'")
                .map { row, _ -> (row.get("count") as Number).toInt() }
                .one()
                .block()
        assertEquals(1, tableCount)
        val migrationCount =
            db.databaseClient
                .sql("SELECT COUNT(*) AS count FROM \"flyway_schema_history\" WHERE \"version\" IN ('1', '2', '3') AND \"success\" = TRUE")
                .map { row, _ -> (row.get("count") as Number).toInt() }
                .one()
                .block()
        assertEquals(3, migrationCount)
        val tables =
            db.databaseClient
                .sql("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")
                .map { row, _ -> row.get("TABLE_NAME", String::class.java) ?: error("Missing table name") }
                .all()
                .collectList()
                .block()
                .orEmpty()
        assertTrue("SUB_AGENT_CONFIGS" in tables, "R2DBC schema tables: $tables")
        db.close()

        val reopened = KnowledgeDbTestFactory.create(databasePath.toString())
        assertEquals("persisted", reopened.preferenceStore.getPreference("probe"))
        reopened.close()
    }

    @Test
    fun `legacy schema is migrated and timeline sequence continues after existing values`() {
        val databasePath = Files.createTempDirectory("visual-agent-r2dbc-legacy").resolve("database")
        createCurrentV1SchemaWithTimelineValue(databasePath)
        val db = KnowledgeDbTestFactory.create(databasePath.toString())

        val newId = "22222222-2222-4222-8222-222222222222"
        db.saveConversationMessage(newId, "main", "user", "new message")

        assertEquals(8, db.conversationStore.getConversationMessage(newId)?.timelineSequence)
        db.close()
    }

    @Test
    fun `V3 migration preserves legacy conversation rows as ungrouped history`() {
        val databasePath = Files.createTempDirectory("visual-agent-v2-upgrade").resolve("database")
        val legacyIds = createV2DatabaseWithConversationHistory(databasePath)

        KnowledgeDbTestFactory.create(databasePath.toString()).use { db ->
            val history = db.conversationStore.getConversationMessages("main", 20).associateBy { it.id }

            assertEquals(3, history.size)
            assertEquals("legacy user prompt", history.getValue(legacyIds.userId).content)
            assertEquals("What did the old assistant say?", history.getValue(legacyIds.assistantId).content)
            assertEquals("legacy tool result", history.getValue(legacyIds.toolId).content)
            assertEquals("{\"type\":\"tool_call\",\"tool\":\"todos\"}", history.getValue(legacyIds.toolId).metadata)
            assertEquals(listOf(1L, 2L, 3L), history.values.sortedBy { it.timelineSequence }.map { it.timelineSequence })
            assertTrue(history.values.all { it.parentAssistantTurnId == null })
            assertTrue(history.values.all { it.turnOrder == null })
            assertTrue(history.values.none { it.assistantToolTurn })
            assertTrue(history.values.all { it.conversationRequestId == null })

            val successfulVersions =
                db.databaseClient
                    .sql(
                        "SELECT \"version\" FROM \"flyway_schema_history\" " +
                            "WHERE \"success\" = TRUE AND \"version\" IS NOT NULL ORDER BY \"installed_rank\"",
                    ).map { row, _ -> row.get("version", String::class.java) ?: error("Missing migration version") }
                    .all()
                    .collectList()
                    .block()
                    .orEmpty()
            assertEquals(listOf("1", "2", "3"), successfulVersions)
        }
    }

    @Test
    fun `legacy file database created with an empty user remains accessible`() {
        val databasePath = Files.createTempDirectory("visual-agent-r2dbc-empty-user").resolve("database")
        createCurrentV1SchemaWithTimelineValue(databasePath, user = "")

        KnowledgeDbTestFactory.create(databasePath.toString()).use { db ->
            val id = "33333333-3333-4333-8333-333333333333"
            db.saveConversationMessage(id, "main", "user", "legacy credentials")
            assertEquals("legacy credentials", db.conversationStore.getConversationMessage(id)?.content)
        }
    }

    @Test
    fun `old preference-only schema is upgraded before reactive stores start`() {
        val databasePath = Files.createTempDirectory("visual-agent-r2dbc-preferences-only").resolve("database")
        createPreferenceOnlyLegacySchema(databasePath)
        val db = KnowledgeDbTestFactory.create(databasePath.toString())

        assertEquals("legacy", db.preferenceStore.getPreference("legacy.key"))
        val tables =
            db.databaseClient
                .sql("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")
                .map { row, _ -> row.get("TABLE_NAME", String::class.java) ?: error("Missing table name") }
                .all()
                .collectList()
                .block()
                .orEmpty()
        assertTrue("CONVERSATION_HISTORY" in tables, "Migrated schema tables: $tables")
        assertTrue("SUB_AGENT_CONFIGS" in tables, "Migrated schema tables: $tables")
        db.close()
    }

    private fun createCurrentV1SchemaWithTimelineValue(
        databasePath: Path,
        user: String = "sa",
    ) {
        val dataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:file:$databasePath;DB_CLOSE_ON_EXIT=FALSE")
                this.user = user
                password = ""
            }
        ResourceDatabasePopulator(ClassPathResource("db/migration-h2/V1__initial_h2_schema.sql")).execute(dataSource)
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO conversation_history
                        (id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, "11111111-1111-4111-8111-111111111111")
                    statement.setString(2, "main")
                    statement.setString(3, "user")
                    statement.setString(4, "legacy message")
                    statement.setObject(5, null)
                    statement.setString(6, "2026-01-01T00:00:00Z")
                    statement.setLong(7, 7)
                    statement.setString(8, "DIALOGUE")
                    statement.executeUpdate()
                }
        }
    }

    private fun createV2DatabaseWithConversationHistory(databasePath: Path): LegacyConversationIds {
        val dataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:file:$databasePath;DB_CLOSE_ON_EXIT=FALSE")
                user = "sa"
                password = ""
            }
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration-h2")
            .target(MigrationVersion.fromVersion("2"))
            .load()
            .migrate()

        val ids =
            LegacyConversationIds(
                userId = "11111111-1111-4111-8111-111111111111",
                assistantId = "22222222-2222-4222-8222-222222222222",
                toolId = "33333333-3333-4333-8333-333333333333",
            )
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO conversation_history
                        (id, session_id, role, content, metadata, created_at, timeline_sequence, context_policy)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    listOf(
                        LegacyConversationRow(ids.userId, "user", "legacy user prompt", null, 1, "DIALOGUE"),
                        LegacyConversationRow(ids.assistantId, "assistant", "What did the old assistant say?", null, 2, "DIALOGUE"),
                        LegacyConversationRow(
                            ids.toolId,
                            "tool",
                            "legacy tool result",
                            "{\"type\":\"tool_call\",\"tool\":\"todos\"}",
                            3,
                            "AUDIT_ONLY",
                        ),
                    ).forEach { row ->
                        statement.setString(1, row.id)
                        statement.setString(2, "main")
                        statement.setString(3, row.role)
                        statement.setString(4, row.content)
                        statement.setString(5, row.metadata)
                        statement.setString(6, "2026-01-01T00:00:00Z")
                        statement.setLong(7, row.timelineSequence)
                        statement.setString(8, row.contextPolicy)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
        }
        return ids
    }

    private data class LegacyConversationIds(
        val userId: String,
        val assistantId: String,
        val toolId: String,
    )

    private data class LegacyConversationRow(
        val id: String,
        val role: String,
        val content: String,
        val metadata: String?,
        val timelineSequence: Long,
        val contextPolicy: String,
    )

    private fun createPreferenceOnlyLegacySchema(databasePath: Path) {
        val dataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:file:$databasePath;DB_CLOSE_ON_EXIT=FALSE")
                user = "sa"
                password = ""
            }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE user_preferences (
                        preference_key VARCHAR(512) PRIMARY KEY,
                        preference_value VARCHAR(1000000) NOT NULL,
                        preference_type VARCHAR(64) NOT NULL DEFAULT 'string',
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.trimIndent(),
                )
            }
            connection
                .prepareStatement(
                    "INSERT INTO user_preferences (preference_key, preference_value, preference_type) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, "legacy.key")
                    statement.setString(2, "legacy")
                    statement.setString(3, "string")
                    statement.executeUpdate()
                }
        }
    }
}
