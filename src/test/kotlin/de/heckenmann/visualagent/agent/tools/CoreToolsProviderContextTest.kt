package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.config.AppConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreToolsProviderContextTest {
    @Test
    fun `context reports openai status without leaking api key`() {
        val originalProvider = AppConfig.instance.llmProvider
        val originalKey = AppConfig.instance.openAiApiKey
        val originalBaseUrl = AppConfig.instance.openAiBaseUrl
        val originalModel = AppConfig.instance.openAiModel
        try {
            AppConfig.instance.llmProvider = "openai"
            AppConfig.instance.openAiApiKey = "sk-secret-value"
            AppConfig.instance.openAiBaseUrl = "https://openai-compatible.example"
            AppConfig.instance.openAiModel = "gpt-context"

            val result = ContextTool().execute("""{}""", emptyMap())

            assertTrue(result.content.contains("Provider: openai"))
            assertTrue(result.content.contains("Model: gpt-context"))
            assertTrue(result.content.contains("OpenAI API key configured: true"))
            assertFalse(result.content.contains("sk-secret-value"))
        } finally {
            AppConfig.instance.llmProvider = originalProvider
            AppConfig.instance.openAiApiKey = originalKey
            AppConfig.instance.openAiBaseUrl = originalBaseUrl
            AppConfig.instance.openAiModel = originalModel
        }
    }
}
