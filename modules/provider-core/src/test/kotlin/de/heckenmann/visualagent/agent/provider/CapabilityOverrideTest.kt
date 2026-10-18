package de.heckenmann.visualagent.agent.provider

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.supportsToolCalling
import de.heckenmann.visualagent.model.CapabilitySupport
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards persisted per-feature overrides, discovery merges and native tool request decisions. */
class CapabilityOverrideTest {
    @Test
    fun `refresh updates provider evidence and retains only targeted overrides`() {
        val model = ProviderModelConfig("partial", capabilities = setOf("vision"), capabilityOverrides = mapOf("vision" to false))
        val discovered = ProviderModelConfig("partial", capabilities = setOf("chat", "tools", "vision"), capabilitiesComplete = true)
        val merged = listOf(discovered).mergeWithExisting(mapOf(model.id to model)).single()
        assertEquals(discovered.capabilities, merged.capabilities)
        assertEquals(mapOf("vision" to false), merged.capabilityOverrides)
        assertTrue(merged.supportsCapability(ModelCapability.CHAT))
        assertFalse(merged.supportsCapability(ModelCapability.VISION))
        assertEquals(CapabilitySupport.SUPPORTED, merged.capabilitySupport(ModelCapability.TOOLS))
        assertEquals(merged, Json.decodeFromString<ProviderModelConfig>(Json.encodeToString(merged)))
    }

    @Test
    fun `old declarations remain compatible and do not invent targeted overrides`() {
        val model =
            Json.decodeFromString<ProviderModelConfig>(
                """{"id":"legacy","capabilities":["vision"],"capabilitiesComplete":true,"capabilitiesOverridden":true}""",
            )
        assertEquals(emptyMap(), model.capabilityOverrides)
        assertFalse(model.supportsCapability(ModelCapability.CHAT))
    }

    @Test
    fun `native request tool guard honors negative override without making unrelated features complete`() {
        val request = ChatRequestContext(messages = emptyList(), modelCapabilities = setOf("vision"))
        assertTrue(request.supportsToolCalling())
        assertTrue(request.copy(modelCapabilityOverrides = mapOf("vision" to false)).supportsToolCalling())
        assertFalse(request.copy(modelCapabilityOverrides = mapOf("tools" to false)).supportsToolCalling())
        assertTrue(request.copy(modelCapabilitiesComplete = true, modelCapabilityOverrides = mapOf("tools" to true)).supportsToolCalling())
    }
}
