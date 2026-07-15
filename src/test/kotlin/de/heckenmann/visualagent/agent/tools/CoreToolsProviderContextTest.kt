package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.config.AppConfigBean
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreToolsProviderContextTest {
    private val appConfig = AppConfigBean()

    @Test
    fun `context reports openai status without leaking api key`() {
        val originalProvider = appConfig.llmProvider
        val originalKey = appConfig.openAiApiKey
        val originalBaseUrl = appConfig.openAiBaseUrl
        val originalModel = appConfig.openAiModel
        try {
            appConfig.llmProvider = "openai"
            appConfig.openAiApiKey = "sk-secret-value"
            appConfig.openAiBaseUrl = "https://openai-compatible.example"
            appConfig.openAiModel = "gpt-context"

            val result = ContextTool(appConfig = appConfig).execute("""{}""", emptyMap())

            assertTrue(result.content.contains("Provider: openai"))
            assertTrue(result.content.contains("Model: gpt-context"))
            assertTrue(result.content.contains("OpenAI API key configured: true"))
            assertFalse(result.content.contains("sk-secret-value"))
        } finally {
            appConfig.llmProvider = originalProvider
            appConfig.openAiApiKey = originalKey
            appConfig.openAiBaseUrl = originalBaseUrl
            appConfig.openAiModel = originalModel
        }
    }
}
