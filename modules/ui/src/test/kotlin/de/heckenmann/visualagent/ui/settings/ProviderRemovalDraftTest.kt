package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.ui.settings.providers.ProviderManagementPanel
import de.heckenmann.visualagent.ui.settings.providers.ProviderManagementState
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Removed providers must not retain invisible invalid option drafts that block all saving. */
class ProviderRemovalDraftTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `removing an inactive provider discards its invalid model options`() {
        val active = ProviderProfile("active", "Active", ProviderAdapter.OLLAMA, "http://localhost")
        val removed = active.copy(id = "removed", name = "Removed", models = listOf(ProviderModel("model")))
        val state = ProviderManagementState()
        state.persisted = ProviderSettingsDraft(providers = listOf(active, removed), providerId = "active")
        state.draft = state.persisted.copy(providerId = "removed", modelId = "model")
        state.loaded = true
        state.modelOptions["removed/model"] = "incomplete-key"
        state.optionsDirty = true
        val providers = mockk<ProviderPort>(relaxed = true)
        val settings = mockk<SettingsPort>(relaxed = true)
        compose.setContent { MaterialTheme { ProviderManagementPanel(settings, providers, state) } }
        compose.onNodeWithContentDescription("Remove provider").performClick()
        compose.onNodeWithText("Save changes").assertIsEnabled()
        compose.runOnIdle { assertTrue(state.modelOptions.isEmpty()) }
    }
}
