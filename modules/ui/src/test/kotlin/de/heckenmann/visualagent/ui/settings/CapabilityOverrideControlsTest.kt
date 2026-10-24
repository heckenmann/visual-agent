package de.heckenmann.visualagent.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.ui.settings.providers.ProviderModelCatalog
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises model catalog visibility and recovery actions without timing assumptions. */
class CapabilityOverrideControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `legacy declarations are not presented as automatic provider evidence`() {
        val legacy = ProviderModel("legacy", capabilities = setOf("vision"), capabilitiesComplete = true, capabilitiesOverridden = true)
        compose.setContent {
            ProviderCatalogTestHost { ModelCapabilitiesEditor(legacy) { _, _ -> } }
        }
        compose.onNodeWithText("CHAT").performClick()
        compose.onNodeWithText("Automatic (unsupported)").assertDoesNotExist()
        compose
            .onNodeWithText(
                "Reset the legacy manual declaration before choosing Automatic; no provider evidence is available for this declaration.",
            ).assertIsDisplayed()
    }

    @Test
    fun `model with persisted legacy vision only declaration remains visible in settings`() {
        val legacy =
            ProviderModel("legacy-partial", capabilities = setOf("vision"), capabilitiesComplete = true, capabilitiesOverridden = true)
        val draft = mutableStateOf(catalogDraft(listOf(legacy)))
        compose.setContent {
            ProviderCatalogTestHost {
                ProviderModelCatalog(draft.value, false, onChange = { draft.value = it }, onRefresh = {})
            }
        }
        assertTrue(
            compose.onNodeWithText("Model catalog").getBoundsInRoot().bottom <=
                compose.onNodeWithContentDescription("Refresh models").getBoundsInRoot().top,
            "The test host must place catalog controls vertically instead of overlaying their click targets",
        )
        compose.onNodeWithText("Model catalog").performClick()
        compose.onNodeWithText("legacy-partial").performClick()
        assertEquals(legacy.id, draft.value.modelId)
        compose
            .onNodeWithText(
                "This model's current capability declaration excludes chat. Review its advanced capability settings; the model remains visible in this catalog.",
            ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Advanced model configuration").performScrollTo().performClick()
        compose.onNodeWithText("CHAT").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `main dropdown keeps partial model after unrelated capability edit`() {
        val partial = ProviderModel("partial", capabilityOverrides = mapOf("vision" to false))
        val blocked = ProviderModel("blocked", capabilityOverrides = mapOf("chat" to false))
        val draft = mutableStateOf(catalogDraft(listOf(partial, blocked)))
        compose.setContent {
            ProviderCatalogTestHost {
                ProviderModelCatalog(draft.value, false, onChange = { draft.value = it }, onRefresh = {})
            }
        }
        compose.onNodeWithText("Model catalog").performClick()
        compose.onNodeWithText("blocked").assertIsDisplayed()
        compose.onNodeWithText("partial").performClick()
        assertEquals("partial", draft.value.modelId)
        assertEquals(mapOf("chat" to false), blocked.capabilityOverrides)
    }

    @Test
    fun `hidden legacy model can be reset without selecting it`() {
        val legacy = ProviderModel("hidden", capabilities = setOf("vision"), capabilitiesComplete = true, capabilitiesOverridden = true)
        val draft =
            mutableStateOf(
                ProviderSettingsDraft(
                    providerId = "provider",
                    providers =
                        listOf(
                            ProviderProfile("provider", "Provider", ProviderAdapter.CODEX_CLI, "", models = listOf(legacy)),
                        ),
                ),
            )
        compose.setContent {
            ProviderCatalogTestHost {
                LegacyCapabilityReset(draft.value) { draft.value = draft.value.resetLegacyCapabilities("provider") }
            }
        }
        compose.onNodeWithContentDescription("Reset legacy capability overrides for this provider").performClick()
        compose.onNodeWithContentDescription("Reset legacy capability overrides for this provider").assertDoesNotExist()
        assertFalse(
            draft.value.providers
                .single()
                .models
                .single()
                .capabilitiesComplete,
        )
    }

    private fun catalogDraft(models: List<ProviderModel>): ProviderSettingsDraft =
        ProviderSettingsDraft(
            providerId = "provider",
            providers = listOf(ProviderProfile("provider", "Provider", ProviderAdapter.CODEX_CLI, "", models = models)),
        )
}
