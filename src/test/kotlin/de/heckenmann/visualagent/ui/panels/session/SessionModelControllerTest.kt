package de.heckenmann.visualagent.ui.panels.session

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.ui.panels.FxTestSupport
import io.mockk.coEvery
import io.mockk.mockk
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionModelControllerTest {
    @Test
    fun `model refresh keeps catalog models visible when discovery fails`() {
        val originalProvider = AppConfig.instance.llmProvider
        val originalOpenAiModel = AppConfig.instance.openAiModel
        try {
            AppConfig.instance.llmProvider = "openai"
            AppConfig.instance.openAiModel = "gpt-local"
            val catalog = ProviderCatalogService(MapPreferenceStore())
            catalog.setActiveProvider("openai")
            val profile = requireNotNull(catalog.getProvider("openai"))
            catalog.saveProvider(
                profile.copy(
                    defaultModel = "gpt-local",
                    models = listOf(ProviderModelConfig("gpt-local")),
                ),
            )
            val provider =
                mockk<LLMProvider> {
                    coEvery { getModels("openai") } throws IllegalStateException("OpenAI API key is not configured")
                }
            lateinit var modelSelector: ComboBox<String>
            lateinit var infoLabel: Label

            FxTestSupport.run {
                modelSelector = ComboBox()
                infoLabel = Label()
                val controller =
                    SessionModelController(
                        modelSelector = modelSelector,
                        modelSearchField = TextField(),
                        favoritesOnlyToggle = CheckBox(),
                        favoriteButton = Button(),
                        refreshModelsButton = Button(),
                        ollamaSettingsGroup = VBox(),
                        openAiSettingsGroup = VBox(),
                        modelInfoLabel = infoLabel,
                        providerCatalog = catalog,
                    )

                controller.setLlmProvider(provider)
                Unit
            }

            Thread.sleep(250)
            FxTestSupport.flush()

            FxTestSupport.run {
                assertEquals(listOf("gpt-local"), modelSelector.items.toList())
                assertEquals("gpt-local", modelSelector.selectionModel.selectedItem)
                assertFalse(modelSelector.isDisable)
                assertTrue(infoLabel.text.contains("Authentication failed") || infoLabel.text.contains("API key"))
            }
        } finally {
            AppConfig.instance.llmProvider = originalProvider
            AppConfig.instance.openAiModel = originalOpenAiModel
        }
    }

    private class MapPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, String>()

        override fun getPreference(key: String): String? = values[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
