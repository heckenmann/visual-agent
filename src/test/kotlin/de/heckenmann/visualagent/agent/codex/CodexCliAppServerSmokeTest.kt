package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ConfiguredLLMProvider
import de.heckenmann.visualagent.agent.OllamaClient
import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.knowledge.PreferenceStore
import io.github.vupoint.cokit.client.CodexRpc
import io.github.vupoint.cokit.client.models.ModelListParams
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Path
import kotlin.test.assertTrue

internal class CodexCliAppServerSmokeTest {
    @Test
    fun `connects to real cli and loads models through cokit`() =
        runBlocking {
            assumeTrue(System.getProperty("visualagent.codex.smoke") == "true")
            val processFactory = CodexCliProcessFactory()
            val locator = CodexCliLocator(SystemCodexCliEnvironment(), ProcessCodexCliVersionProbe(processFactory))
            val location = locator.locate(null)
            assertTrue(location is CodexCliLocation.Ready)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val connectionFactory = CodexAppServerConnectionFactory(processFactory, scope)
                val provider = CodexCliProvider(locator, connectionFactory, mockk<ToolRegistry>(relaxed = true))
                val model =
                    connectionFactory
                        .connect(location.executable, Path.of(System.getProperty("user.dir")))
                        .use { connection ->
                            val result = connection.client.request(CodexRpc.Model.List, ModelListParams(limit = 10))
                            assertTrue(result.data.isNotEmpty())
                            result.data
                                .first()
                                .model.value
                        }
                val chatModel =
                    CodexCliChatModel(
                        CoKitCodexAppServerChatBridge(
                            connectionFactory,
                            location.executable,
                            Path.of(System.getProperty("user.dir")),
                            model,
                        ),
                    )
                val responseText =
                    chatModel
                        .call(Prompt("Reply with exactly OK."))
                        .result
                        ?.output
                        ?.text
                assertTrue(!responseText.isNullOrBlank())
                verifyCatalogModelRefresh(provider)
            } finally {
                scope.cancel()
            }
        }

    private suspend fun verifyCatalogModelRefresh(provider: CodexCliProvider) {
        val store = InMemoryPreferenceStore()
        val catalog = ProviderCatalogService(store)
        val profile =
            ProviderProfile(
                id = "codex-smoke",
                name = "Codex smoke",
                adapter = ProviderAdapter.CODEX_CLI,
                baseUrl = "",
            )
        catalog.saveProvider(profile)
        val router =
            ConfiguredLLMProvider(
                mockk<OllamaClient>(relaxed = true),
                mockk<OpenAiClient>(relaxed = true),
                catalog,
                codexCliProvider = provider,
            )

        val discovered = router.getModels(profile.id)

        assertTrue(discovered.isNotEmpty())
        assertTrue(catalog.selectableModels(profile.id).map { it.id }.containsAll(discovered))
    }

    private class InMemoryPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, String>()

        override fun getPreference(key: String): String? = values[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }
}
