package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals

/** Verifies onboarding state initialization and idempotent H2 R2DBC schema setup. */
class OnboardingStateMigrationTest {
    @Test
    fun `fresh database starts onboarding`() {
        val databasePath = Files.createTempDirectory("visual-agent-onboarding-fresh").resolve("database")
        val db = KnowledgeDbTestFactory.create(databasePath.toString())

        assertEquals("NOT_STARTED", db.preferenceStore.getPreference("ui.onboarding.v1"))
        db.close()
    }

    @Test
    fun `existing onboarding state survives a repeated schema initialization`() {
        val databasePath = Files.createTempDirectory("visual-agent-onboarding-existing").resolve("database")
        val db = KnowledgeDbTestFactory.create(databasePath.toString())
        db.preferenceStore.setPreference("ui.onboarding.v1", "COMPLETED")
        db.close()

        val reopened = KnowledgeDbTestFactory.create(databasePath.toString())
        assertEquals("COMPLETED", reopened.preferenceStore.getPreference("ui.onboarding.v1"))
        reopened.close()
    }
}
