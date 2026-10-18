package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.capabilitySupport
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Prevents editing one capability from removing the default chat capability. */
class ModelCapabilitiesEditorTest {
    @Test
    fun `partial vision metadata retains unknown chat when editing`() {
        val partial = ProviderModel("partial", capabilities = setOf("vision"))
        assertEquals(CapabilitySupport.UNKNOWN, partial.capabilitySupport("chat"))
    }

    @Test
    fun `editing vision does not declare unrelated capabilities unsupported`() {
        val partial = ProviderModel("partial", capabilities = setOf("vision"))
        val draft =
            ProviderSettingsDraft(
                providers = listOf(ProviderProfile("provider", "Provider", ProviderAdapter.CODEX_CLI, "", models = listOf(partial))),
            )
        val updated =
            draft
                .withModelCapabilities("provider", "partial", "vision", false)
                .providers
                .single()
                .models
                .single()
        assertEquals(CapabilitySupport.UNKNOWN, updated.capabilitySupport("chat"))
        assertEquals(CapabilitySupport.UNKNOWN, updated.capabilitySupport("tools"))
        assertFalse(updated.capabilitiesComplete, "An edit must not make incomplete provider metadata authoritative")
        assertFalse(updated.capabilitiesOverridden, "A targeted override must not replace the entire provider declaration")
    }

    @Test
    fun `editing unknown models retains default chat capability`() {
        val model = ProviderModel("unknown", capabilityOverrides = mapOf("vision" to true))
        assertEquals(CapabilitySupport.UNKNOWN, model.capabilitySupport("chat"))
        assertEquals(CapabilitySupport.SUPPORTED, model.capabilitySupport("vision"))
    }

    @Test
    fun `complete non chat models do not gain chat implicitly`() {
        val imageOnly = ProviderModel("image", capabilities = setOf("image_generation"), capabilitiesComplete = true)
        assertEquals(CapabilitySupport.UNSUPPORTED, imageOnly.capabilitySupport("chat"))
    }
}
