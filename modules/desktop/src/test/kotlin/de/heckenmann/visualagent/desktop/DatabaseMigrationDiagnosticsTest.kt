package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.knowledge.DatabaseMigrationFailure
import de.heckenmann.visualagent.knowledge.DatabaseMigrationFailureKind
import org.junit.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies that desktop startup recognizes safely reportable migration failures. */
class DatabaseMigrationDiagnosticsTest {
    @Test
    fun `wrapped migration failure exposes recovery location but not raw SQL error`() {
        val migration =
            DatabaseMigrationFailure(
                DatabaseMigrationFailureKind.MIGRATION_FAILED,
                Path.of("/tmp/migration-backups/snapshot-1"),
                IllegalStateException("password=secret SQL SELECT preference_value"),
            )
        val startup = IllegalStateException("Spring startup failed", migration)

        assertEquals(migration, startup.databaseMigrationFailure())
        assertTrue(migration.userMessage().contains("/tmp/migration-backups/snapshot-1"))
        assertFalse(migration.userMessage().contains("password=secret"))
        assertFalse(migration.userMessage().contains("SELECT"))
    }

    @Test
    fun `unrelated failure is not labeled a migration failure`() {
        assertNull(IllegalStateException("network unavailable").databaseMigrationFailure())
    }
}
