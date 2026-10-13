package de.heckenmann.visualagent.ui.agents

import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Capability assignments must honor the same provider selection constraints as the dropdown. */
class SubAgentCapabilitiesEditorTest {
    private val enabled = setOf("CHAT", "VISION")
    private val assigned = mapOf("VISION" to "openai::vision")
    private val visionModel = ProviderModel("vision", capabilities = setOf("vision"))

    private fun profile(
        whitelist: Set<String> = emptySet(),
        blacklist: Set<String> = emptySet(),
    ) = ProviderProfile(
        id = "openai",
        name = "OpenAI",
        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
        baseUrl = "https://api.openai.com",
        models = listOf(visionModel),
        modelWhitelist = whitelist,
        modelBlacklist = blacklist,
    )

    @Test
    fun `accepts an enabled and selectable specialized model`() {
        assertTrue(validCapabilityAssignments(enabled, assigned, listOf(profile())))
    }

    @Test
    fun `rejects blocked or excluded capability models`() {
        assertFalse(validCapabilityAssignments(enabled, assigned, listOf(profile(blacklist = setOf("vision")))))
        assertFalse(validCapabilityAssignments(enabled, assigned, listOf(profile(whitelist = setOf("different")))))
    }
}
