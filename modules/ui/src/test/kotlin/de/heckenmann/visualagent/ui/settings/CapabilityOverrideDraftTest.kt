package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.capabilitySupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Protects partial metadata, explicit denials and recovery of hidden legacy models. */
class CapabilityOverrideDraftTest {
    @Test
    fun `catalog remains editable but incompatible main and interpolation choices cannot be activated`() {
        val incompatible = model.copy(capabilityOverrides = mapOf("chat" to false))
        assertNotNull(draft(incompatible).modelSelectionError())
        val compatible = draft(incompatible).withModelCapabilities("provider", model.id, "chat", true)
        assertNull(compatible.modelSelectionError())
        val explicit =
            compatible.copy(
                conversationSettings =
                    compatible.conversationSettings.copy(
                        promptInterpolationProviderId = "missing",
                        promptInterpolationModelId = model.id,
                    ),
            )
        assertNotNull(explicit.modelSelectionError())
    }

    private val model = ProviderModel("partial", capabilities = setOf("vision"))

    private fun draft(model: ProviderModel = this.model) =
        ProviderSettingsDraft(
            providers = listOf(ProviderProfile("provider", "Provider", ProviderAdapter.CODEX_CLI, "", models = listOf(model))),
            providerId = "provider",
            modelId = model.id,
        )

    @Test
    fun `automatic removes only the selected override`() {
        val edited =
            draft()
                .withModelCapabilities("provider", model.id, "tools", false)
                .withModelCapabilities("provider", model.id, "vision", false)
                .withModelCapabilities("provider", model.id, "tools", null)
                .providers
                .single()
                .models
                .single()
        assertEquals(mapOf("vision" to false), edited.capabilityOverrides)
        assertEquals(CapabilitySupport.UNKNOWN, edited.capabilitySupport("chat"))
        assertEquals(CapabilitySupport.UNKNOWN, edited.capabilitySupport("tools"))
        assertEquals(setOf("vision"), edited.capabilities)
        assertFalse(edited.capabilitiesComplete)
    }

    @Test
    fun `late discovery cannot erase targeted changes or restore a reset legacy declaration`() {
        val response = model.copy(capabilities = setOf("completion", "vision"), capabilitiesComplete = true)
        val edited =
            draft()
                .withModelCapabilities("provider", model.id, "tools", false)
                .withModels("provider", listOf(response))
                .providers
                .single()
                .models
                .single()
        assertEquals(response.capabilities, edited.capabilities)
        assertEquals(mapOf("tools" to false), edited.capabilityOverrides)

        val legacy = model.copy(capabilitiesComplete = true, capabilitiesOverridden = true)
        val reset =
            draft(legacy)
                .resetLegacyCapabilities("provider")
                .withModels("provider", listOf(legacy))
                .providers
                .single()
                .models
                .single()
        assertFalse(reset.capabilitiesOverridden)
        assertEquals(CapabilitySupport.UNKNOWN, reset.capabilitySupport("chat"))
    }

    @Test
    fun `reset legacy whole list declaration keeps targeted overrides and other providers unchanged`() {
        val legacy = model.copy(capabilitiesComplete = true, capabilitiesOverridden = true, capabilityOverrides = mapOf("tools" to false))
        val original = draft(legacy)
        val reset =
            original
                .resetLegacyCapabilities("provider")
                .providers
                .single()
                .models
                .single()
        assertEquals(CapabilitySupport.UNKNOWN, reset.capabilitySupport("chat"))
        assertEquals(CapabilitySupport.UNSUPPORTED, reset.capabilitySupport("tools"))
        assertFalse(reset.capabilitiesOverridden)
        assertEquals(original, original.resetLegacyCapabilities("other"))
        assertEquals(draft(), draft().resetLegacyCapabilities("provider"))
    }
}
