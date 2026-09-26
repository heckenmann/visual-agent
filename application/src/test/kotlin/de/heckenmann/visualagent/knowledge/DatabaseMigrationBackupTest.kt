package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.canvas.CanvasDocumentCodec
import de.heckenmann.visualagent.canvas.CanvasFigureSnapshot
import de.heckenmann.visualagent.canvas.CanvasSnapshot
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.h2.jdbcx.JdbcDataSource
import org.h2.tools.Restore
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises a real pre-migration H2 archive and managed workspace snapshot. */
class DatabaseMigrationBackupTest {
    @Test
    fun `V2 upgrade preserves durable records and creates a restorable pre-migration snapshot`() {
        val root = Files.createTempDirectory("visual-agent-migration-backup")
        val databasePath = root.resolve("database")
        val source = dataSource(databasePath)
        migrateToV2(source)
        val image = byteArrayOf(1, 3, 5, 7, 9)
        val imported = root.resolve("workspace/imports/photo.png")
        Files.createDirectories(imported.parent)
        Files.write(imported, image)
        val canvas = root.resolve("workspace/canvas/current.canvas")
        Files.createDirectories(canvas.parent)
        val canvasContent =
            CanvasDocumentCodec.encode(
                CanvasSnapshot(
                    figureCount = 1,
                    zoomPercent = 125,
                    gridVisible = true,
                    figures = listOf(CanvasFigureSnapshot(0, "rectangle", 10.0, 20.0, 80.0, 40.0, color = "#ff0000")),
                ),
            )
        Files.writeString(canvas, canvasContent)
        seedRecords(source, image)

        KnowledgeDbTestFactory.create(databasePath.toString()).use { db ->
            assertEquals("stored answer", query(db.databaseClient, "SELECT content FROM conversation_history WHERE id='message-1'"))
            assertEquals("Investigate", query(db.databaseClient, "SELECT description FROM todos WHERE id='todo-1'"))
            assertEquals("Researcher", query(db.databaseClient, "SELECT name FROM sub_agents WHERE id='agent-1'"))
            assertEquals("[\"file_read\"]", query(db.databaseClient, "SELECT tools FROM sub_agent_configs WHERE id='config-1'"))
            assertEquals("model-x", db.preferenceStore.getPreference("llm.provider.catalog.v1"))
            assertEquals("imports/photo.png", query(db.databaseClient, "SELECT relative_path FROM workspace_files WHERE id='file-1'"))
            assertEquals(sha256(image), query(db.databaseClient, "SELECT sha256 FROM workspace_files WHERE id='file-1'"))
            assertContentEquals(image, Files.readAllBytes(imported))
            assertEquals(canvasContent, Files.readString(canvas))
            assertEquals(
                "rectangle",
                CanvasDocumentCodec
                    .decode(Files.readString(canvas))
                    .figures
                    .single()
                    .type,
            )
        }

        val snapshot =
            Files.list(root.resolve("migration-backups")).use { stream ->
                stream.toList().single()
            }
        assertTrue(snapshot.fileName.toString().startsWith("snapshot-"))
        assertContentEquals(image, Files.readAllBytes(snapshot.resolve("workspace/imports/photo.png")))
        assertEquals(canvasContent, Files.readString(snapshot.resolve("workspace/canvas/current.canvas")))
        assertEquals(
            "rectangle",
            CanvasDocumentCodec
                .decode(Files.readString(snapshot.resolve("workspace/canvas/current.canvas")))
                .figures
                .single()
                .type,
        )
        val restoreRoot = Files.createTempDirectory("visual-agent-migration-restore")
        Restore.execute(snapshot.resolve("database.zip").toString(), restoreRoot.toString(), "database")
        val restoredCanvas = restoreRoot.resolve("workspace/canvas/current.canvas")
        Files.createDirectories(restoredCanvas.parent)
        Files.copy(snapshot.resolve("workspace/canvas/current.canvas"), restoredCanvas)
        val restored = dataSource(restoreRoot.resolve("database"))
        assertEquals("Investigate", query(restored, "SELECT description FROM todos WHERE id='todo-1'"))
        assertEquals("[\"file_read\"]", query(restored, "SELECT tools FROM sub_agent_configs WHERE id='config-1'"))
        assertEquals(sha256(image), query(restored, "SELECT sha256 FROM workspace_files WHERE id='file-1'"))
        assertEquals(
            "model-x",
            query(restored, "SELECT preference_value FROM user_preferences WHERE preference_key='llm.provider.catalog.v1'"),
        )
        assertEquals("2", query(restored, "SELECT MAX(\"version\") FROM \"flyway_schema_history\""))
        assertEquals(125, CanvasDocumentCodec.decode(Files.readString(restoredCanvas)).zoomPercent)

        KnowledgeDbTestFactory.create(databasePath.toString()).close()
        Files.list(root.resolve("migration-backups")).use { stream -> assertEquals(1, stream.count()) }
    }

    @Test
    fun `fresh database starts without a pre-migration snapshot`() {
        val root = Files.createTempDirectory("visual-agent-fresh-migration")
        KnowledgeDbTestFactory.create(root.resolve("database").toString()).close()
        assertFalse(Files.exists(root.resolve("migration-backups")))
    }

    private fun migrateToV2(dataSource: JdbcDataSource) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration-h2")
            .target(MigrationVersion.fromVersion("2"))
            .load()
            .migrate()
    }

    private fun seedRecords(
        dataSource: JdbcDataSource,
        image: ByteArray,
    ) {
        val sha = sha256(image)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO conversation_history (id, session_id, role, content, created_at) " +
                        "VALUES ('message-1','main','assistant','stored answer','2026-01-01T00:00:00Z')",
                )
                statement.executeUpdate("INSERT INTO todos (id, description) VALUES ('todo-1','Investigate')")
                statement.executeUpdate("INSERT INTO sub_agents (id, name, role) VALUES ('agent-1','Researcher','researcher')")
                statement.executeUpdate(
                    "INSERT INTO sub_agent_configs (id, name, description, model, tools) " +
                        "VALUES ('config-1','Researcher','Find sources','model-x','[\"file_read\"]')",
                )
                statement.executeUpdate(
                    "INSERT INTO user_preferences (preference_key, preference_value) " +
                        "VALUES ('llm.provider.catalog.v1','model-x')",
                )
                statement.executeUpdate(
                    "INSERT INTO workspace_files (id, relative_path, original_name, mime_type, size_bytes, sha256) " +
                        "VALUES ('file-1','imports/photo.png','photo.png','image/png',${image.size},'$sha')",
                )
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun dataSource(path: Path): JdbcDataSource =
        JdbcDataSource().apply {
            setURL("jdbc:h2:file:$path;DB_CLOSE_ON_EXIT=FALSE")
            user = "sa"
            password = ""
        }

    private fun query(
        dataSource: JdbcDataSource,
        sql: String,
    ): String? =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result -> if (result.next()) result.getString(1) else null }
            }
        }

    private fun query(
        client: org.springframework.r2dbc.core.DatabaseClient,
        sql: String,
    ): String? =
        client
            .sql(sql)
            .map { row, _ -> row.get(0, String::class.java) ?: "" }
            .one()
            .block()
}
