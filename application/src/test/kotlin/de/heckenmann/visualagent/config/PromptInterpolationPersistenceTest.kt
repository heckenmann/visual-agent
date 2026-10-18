package de.heckenmann.visualagent.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies optional preferences survive server restart. */
class PromptInterpolationPersistenceTest {
    @Test
    fun `missing preferences keep auto disabled and inherit the active model`() {
        val config = AppConfigBean()
        AppConfigPersistenceBinder(NoOpPreferenceStore(), config, "test.db").bind()
        assertFalse(config.automaticPromptInterpolation)
        assertEquals(8, config.promptInterpolationContextMessages)
        assertEquals("", config.promptInterpolationProviderId)
        assertEquals("", config.promptInterpolationModelId)
    }

    @Test
    fun `saved preferences restore the complete assignment`() {
        val preferences = NoOpPreferenceStore()
        AppConfigBean(preferences)
            .apply {
                automaticPromptInterpolation = true
                promptInterpolationContextMessages = 10
                promptInterpolationProviderId = "remote"
                promptInterpolationModelId = "rewrite"
            }.save()
        val restored = AppConfigBean()
        AppConfigPersistenceBinder(preferences, restored, "test.db").bind()
        assertTrue(restored.automaticPromptInterpolation)
        assertEquals(10, restored.promptInterpolationContextMessages)
        assertEquals("remote", restored.promptInterpolationProviderId)
        assertEquals("rewrite", restored.promptInterpolationModelId)
    }
}
