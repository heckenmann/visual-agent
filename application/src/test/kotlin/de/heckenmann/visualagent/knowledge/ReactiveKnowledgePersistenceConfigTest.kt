package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
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
                .sql("SELECT COUNT(*) AS count FROM \"flyway_schema_history\" WHERE \"version\" IN ('1', '2') AND \"success\" = TRUE")
                .map { row, _ -> (row.get("count") as Number).toInt() }
                .one()
                .block()
        assertEquals(2, migrationCount)
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
