package de.heckenmann.visualagent.ui.components

import de.heckenmann.visualagent.protocol.ProviderModel
import org.junit.Test
import kotlin.test.assertEquals

/** Model dropdown labels must not change when discovery has no capability metadata. */
class ProviderModelLabelsTest {
    @Test
    fun `models without capability declarations retain their legacy display text`() {
        assertEquals("large-model", ProviderModel("large-model").labelWithCapabilities())
        assertEquals("Shown Name", ProviderModel("id", name = "Shown Name").labelWithCapabilities())
    }

    @Test
    fun `known capabilities appear alongside the model name`() {
        val model = ProviderModel("vision", name = "Vision", capabilities = setOf("vision", "tools"))
        assertEquals("Vision [tools, vision]", model.labelWithCapabilities())
    }
}
