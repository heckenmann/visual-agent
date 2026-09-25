package de.heckenmann.visualagent.agent.provider

import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies discovered model metadata refreshes without discarding existing values when absent. */
class ProviderCatalogModelMergeTest {
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
