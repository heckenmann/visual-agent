package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.ui.panels.session.SessionProviderSettingsBinder
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SessionProviderSettingsBinderTest {
    @Test
    fun `binder loads and updates active provider profile`() =
        FxTestSupport.run {
            val originalProvider = AppConfig.instance.llmProvider
            val originalOllamaUrl = AppConfig.instance.ollamaLocalUrl
            val originalOllamaKey = AppConfig.instance.ollamaApiKey
            val originalOpenAiUrl = AppConfig.instance.openAiBaseUrl
            val originalOpenAiKey = AppConfig.instance.openAiApiKey
            try {
                val catalog = ProviderCatalogService(MapPreferenceStore())
                catalog.setActiveProvider("ollama")
                val ollamaKey = PasswordField()
                val ollamaUrl = TextField()
                val openAiKey = PasswordField()
                val openAiUrl = TextField()
                val binder =
                    SessionProviderSettingsBinder(
                        ollamaKey,
                        ollamaUrl,
                        openAiKey,
                        openAiUrl,
                        catalog,
                    )

                binder.bind()
                ollamaKey.text = "secured"
                ollamaUrl.text = "https://ollama.example.test"

                assertEquals("secured", catalog.getProvider("ollama")?.apiKey)
                assertEquals("https://ollama.example.test", catalog.getProvider("ollama")?.baseUrl)

                catalog.setActiveProvider("openai")
                binder.showActiveProvider()
                openAiKey.text = "openai-key"
                openAiUrl.text = "https://openai.example.test"
                assertEquals("openai-key", catalog.getProvider("openai")?.apiKey)
                assertEquals("https://openai.example.test", catalog.getProvider("openai")?.baseUrl)
            } finally {
                AppConfig.instance.llmProvider = originalProvider
                AppConfig.instance.ollamaLocalUrl = originalOllamaUrl
                AppConfig.instance.ollamaApiKey = originalOllamaKey
                AppConfig.instance.openAiBaseUrl = originalOpenAiUrl
                AppConfig.instance.openAiApiKey = originalOpenAiKey
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
