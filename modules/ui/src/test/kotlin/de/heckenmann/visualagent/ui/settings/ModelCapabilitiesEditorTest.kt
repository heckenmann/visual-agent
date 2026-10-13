package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.protocol.ProviderModel
import org.junit.Test
import kotlin.test.assertEquals

/** Prevents editing one capability from removing the default chat capability. */
class ModelCapabilitiesEditorTest {
    @Test
    fun `editing unknown models retains default chat capability`() {
        assertEquals(setOf("CHAT"), ProviderModel("unknown").capabilitiesForEditing())
        assertEquals(
            setOf("CHAT", "VISION"),
            ProviderModel("unknown").capabilitiesForEditing() + "VISION",
        )
    }

    @Test
    fun `complete non chat models do not gain chat implicitly`() {
        val imageOnly = ProviderModel("image", capabilities = setOf("image_generation"), capabilitiesComplete = true)
        assertEquals(setOf("IMAGE_GENERATION"), imageOnly.capabilitiesForEditing())
    }
}
