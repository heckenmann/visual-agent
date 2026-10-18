package de.heckenmann.visualagent.agent.provider

import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies discovered model metadata refreshes without discarding existing values when absent. */
class ProviderCatalogModelMergeTest {
    @Test
    fun `manual capability choices survive renewed provider discovery`() {
        val manual =
            ProviderModelConfig(
                id = "gpt-custom",
                capabilities = setOf("vision"),
                capabilitiesComplete = true,
                capabilitiesOverridden = true,
            )
        val discovered =
            ProviderModelConfig(
                id = "gpt-custom",
                capabilities = setOf("chat", "tools"),
                capabilitiesComplete = false,
            )
        val merged = listOf(discovered).mergeWithExisting(mapOf(manual.id to manual)).single()
        assertEquals(setOf("vision"), merged.capabilities)
        assertEquals(true, merged.capabilitiesComplete)
        assertEquals(true, merged.capabilitiesOverridden)
    }

    @Test
    fun `updated provider capabilities replace stale automatic declarations`() {
        val old = ProviderModelConfig("model", capabilities = setOf("chat"), capabilitiesComplete = true)
        val updated = ProviderModelConfig("model", capabilities = setOf("chat", "vision"), capabilitiesComplete = true)
        val merged = listOf(updated).mergeWithExisting(mapOf(old.id to old)).single()
        assertEquals(setOf("chat", "vision"), merged.capabilities)
        assertEquals(true, merged.capabilitiesComplete)
    }

    @Test
    fun `updates discovered limits while preserving existing limits when discovery omits them`() {
        val existing =
            mapOf(
                "updated" to ProviderModelConfig("updated", contextLimit = 4096, outputLimit = 512),
                "unchanged" to ProviderModelConfig("unchanged", contextLimit = 8192, outputLimit = 1024),
            )

        val merged =
            listOf(
                ProviderModelConfig("updated", contextLimit = 16384, outputLimit = 2048),
                ProviderModelConfig("unchanged"),
            ).mergeWithExisting(existing)

        assertEquals(16384, merged[0].contextLimit)
        assertEquals(2048, merged[0].outputLimit)
        assertEquals(8192, merged[1].contextLimit)
        assertEquals(1024, merged[1].outputLimit)
    }
}
