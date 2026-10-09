package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerDatabaseDiagnosticsPortAdapterTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `reports database reachability and latest successful schema version`() {
        KnowledgeDbTestFactory.create(temporaryDirectory.resolve("database").toString()).use { database ->
            val result = ServerDatabaseDiagnosticsPortAdapter(database.databaseClient).snapshot().block()!!

            assertTrue(result.reachable)
            assertTrue(result.schemaHistoryAvailable)
            assertEquals("5", result.currentSchemaVersion)
            assertEquals(0, result.failedMigrationCount)
        }
    }
}
