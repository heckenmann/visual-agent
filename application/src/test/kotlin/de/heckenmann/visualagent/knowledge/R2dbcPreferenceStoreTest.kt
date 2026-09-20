package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Verifies reactive preference CRUD, transaction rollback, and file-backed restart behavior. */
class R2dbcPreferenceStoreTest {
    @Test
    fun `preference values survive restart and updates are transactional`() {
        val databasePath = Files.createTempDirectory("visual-agent-r2dbc-preferences").resolve("database")
        val db = KnowledgeDbTestFactory.create(databasePath.toString())

        db.preferenceStore.setPreferenceReactive("ui.theme", "dark").block()
        assertEquals("dark", db.preferenceStore.getPreferenceReactive("ui.theme").block())

        assertFailsWith<IllegalStateException> {
            db.transactionalOperator
                .transactional(
                    db.databaseClient
                        .sql(
                            """
                            UPDATE user_preferences
                            SET preference_value = 'corrupted'
                            WHERE preference_key = 'ui.theme'
                            """.trimIndent(),
                        ).fetch()
                        .rowsUpdated()
                        .then(Mono.error(IllegalStateException("rollback"))),
                ).block()
        }
        assertEquals("dark", db.preferenceStore.getPreferenceReactive("ui.theme").block())
        db.close()

        val reopened = KnowledgeDbTestFactory.create(databasePath.toString())
        assertEquals("dark", reopened.preferenceStore.getPreferenceReactive("ui.theme").block())
        assertNull(reopened.preferenceStore.getPreferenceReactive("missing").block())
        reopened.close()
    }
}
