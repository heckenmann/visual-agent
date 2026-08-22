package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.codex.CodexCliProvider
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderPreferenceStore
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies provider routing and capability checks for image analysis. */
class ConfiguredLLMProviderVisionTest {
    @Test
    fun `codex vision delegates with the resolved provider profile`() =
        runTest {
            val catalog = catalog()
            catalog.saveProvider(
                ProviderProfile(
                    id = "codex-custom",
                    name = "Codex",
                    adapter = ProviderAdapter.CODEX_CLI,
                    baseUrl = "",
                    defaultModel = "gpt-codex",
                    models = listOf(ProviderModelConfig("gpt-codex", capabilities = setOf("vision"))),
                ),
            )
            catalog.setActiveSelection("codex-custom", "gpt-codex")
            val codex = mockk<CodexCliProvider>()
            coEvery { codex.vision(any(), "describe", "gpt-codex", any()) } returns
                ChatResponse("gpt-codex", Message("assistant", "codex image ok"), done = true)
            val router =
                ConfiguredLLMProvider(
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    catalog,
                    codexCliProvider = codex,
                )

            assertEquals("codex image ok", router.vision(byteArrayOf(1), "describe").message.content)
            coVerify(exactly = 1) { codex.vision(any(), "describe", "gpt-codex", any()) }
        }

    @Test
    fun `vision rejects a model whose catalog explicitly lacks image capability`() =
        runTest {
            val catalog = catalog()
            catalog.saveProvider(
                ProviderProfile(
                    id = "text-only",
                    name = "Text only",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://example.test",
                    defaultModel = "text-model",
                    models = listOf(ProviderModelConfig("text-model", capabilities = setOf("tools"))),
                ),
            )
            catalog.setActiveSelection("text-only", "text-model")
            val error =
                assertFailsWith<IllegalStateException> {
                    ConfiguredLLMProvider(mockk(relaxed = true), mockk(relaxed = true), catalog)
                        .vision(byteArrayOf(1), "describe")
                }

            assertEquals("Model Text only/text-model does not support image input", error.message)
        }

    private fun catalog(): ProviderCatalogService =
        ProviderCatalogService(
            object : ProviderPreferenceStore {
                private val values = mutableMapOf<String, String>()

                override fun getPreference(key: String): String? = values[key]

                override fun setPreference(
                    key: String,
                    value: String,
                ) {
                    values[key] = value
                }
            },
            TestProviderRuntimeConfig(),
        )
}
