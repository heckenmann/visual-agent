package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.ui.settings.providers.ProviderModelCatalog
import de.heckenmann.visualagent.ui.settings.providers.validModelOptions
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Advanced settings must retain incomplete input instead of parsing away each typed character. */
class ProviderModelOptionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `incomplete keys remain editable and only valid options enter the provider draft`() {
        val profile = ProviderProfile("local", "Local", ProviderAdapter.OLLAMA, "http://localhost", models = listOf(ProviderModel("model")))
        var draft by mutableStateOf(ProviderSettingsDraft(providers = listOf(profile), providerId = "local", modelId = "model"))
        compose.setContent { MaterialTheme { ProviderModelCatalog(draft, false, onChange = { draft = it }, onRefresh = {}) } }
        compose.onNodeWithContentDescription("Advanced model configuration").performClick()
        compose.onNodeWithText("Model options (key=value per line)").performTextInput("temperature")
        compose.onNodeWithText("temperature").assertExists()
        compose.runOnIdle {
            assertTrue(
                draft.providers
                    .single()
                    .models
                    .single()
                    .options
                    .isEmpty(),
            )
        }
        compose.onNodeWithText("Model options (key=value per line)").performTextInput("=0.4")
        compose.runOnIdle {
            assertEquals(
                mapOf("temperature" to "0.4"),
                draft.providers
                    .single()
                    .models
                    .single()
                    .options,
            )
        }
        assertFalse(validModelOptions("key=1\n key=2"))
        assertFalse(validModelOptions("=1"))
        assertTrue(validModelOptions("key=value=with=equals"))
    }
}
