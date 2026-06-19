package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.ui.panels.session.ProviderProfileDialog
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.DialogPane
import javafx.scene.control.PasswordField
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.stage.Window
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProviderProfileDialogTest {
    @Test
    fun `create dialog returns provider models filters and options`() =
        FxTestSupport.run {
            var saved: ProviderProfile? = null
            Platform.runLater {
                val pane = activeDialogPane()
                val fields = descendants(pane).filterIsInstance<TextField>()
                fields.first { it.promptText == "example: company-openai" }.text = "company"
                fields.first { it.promptText == "Display name" }.text = "Company AI"
                fields.first { it.promptText == "https://api.example.com" }.text = "https://ai.example.test"
                fields.first { it.promptText == "Default model ID" }.text = "fast"
                fields.first { it.promptText.startsWith("Optional comma") }.text = "fast"
                fields.last { it.promptText.startsWith("Optional comma") }.text = "old"
                descendants(pane).filterIsInstance<PasswordField>().single().text = "secret"
                val areas = descendants(pane).filterIsInstance<TextArea>()
                areas.first { it.promptText.startsWith("timeout") }.text = "timeout=300000"
                areas.first { it.promptText.startsWith("model-id") }.text =
                    "fast|ACTIVE|32000|4096|temperature=0.2;topP=0.8"
                saveButton(pane).fire()
            }

            ProviderProfileDialog.show { saved = it }

            assertEquals("company", saved?.id)
            assertEquals(ProviderAdapter.OPENAI_COMPATIBLE, saved?.adapter)
            assertEquals(mapOf("timeout" to "300000"), saved?.options)
            assertEquals(setOf("fast"), saved?.modelWhitelist)
            assertEquals(setOf("old"), saved?.modelBlacklist)
            assertEquals(4096, saved?.models?.single()?.outputLimit)
            assertEquals(mapOf("temperature" to "0.2", "topP" to "0.8"), saved?.models?.single()?.options)
        }

    @Test
    fun `edit dialog preserves model metadata`() =
        FxTestSupport.run {
            val existing =
                ProviderProfile(
                    id = "ollama-cloud",
                    name = "Ollama Cloud",
                    adapter = ProviderAdapter.OLLAMA,
                    baseUrl = "https://ollama.example.test",
                    defaultModel = "coder",
                    models =
                        listOf(
                            ProviderModelConfig(
                                id = "coder",
                                status = ModelStatus.BETA,
                                contextLimit = 64000,
                                outputLimit = 8192,
                            ),
                        ),
                )
            var saved: ProviderProfile? = null
            Platform.runLater {
                val pane = activeDialogPane()

                @Suppress("UNCHECKED_CAST")
                val adapter = descendants(pane).filterIsInstance<ComboBox<*>>().single() as ComboBox<ProviderAdapter>
                assertEquals(ProviderAdapter.OLLAMA, adapter.value)
                saveButton(pane).fire()
            }

            ProviderProfileDialog.show(existing) { saved = it }

            assertEquals(existing, saved)
        }

    private fun saveButton(pane: DialogPane): Button =
        pane.buttonTypes
            .map(pane::lookupButton)
            .filterIsInstance<Button>()
            .first { it.text == "Save provider" }

    private fun activeDialogPane(): DialogPane =
        Window
            .getWindows()
            .filter { it.isShowing }
            .mapNotNull { it.scene?.root as? DialogPane }
            .last()

    private fun descendants(root: Parent): List<javafx.scene.Node> =
        root.childrenUnmodifiable.flatMap { child ->
            listOf(child) + if (child is Parent) descendants(child) else emptyList()
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
