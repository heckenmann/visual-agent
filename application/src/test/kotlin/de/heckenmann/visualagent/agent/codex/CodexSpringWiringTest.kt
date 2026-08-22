package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ConfiguredLLMProvider
import de.heckenmann.visualagent.agent.OllamaClient
import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.provider.ProfiledProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.config.AppConfigBean
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@SpringBootTest(properties = ["visual-agent.ui.enabled=false", "visual-agent.db.path=jdbc:sqlite::memory:"])
internal class CodexSpringWiringTest {
    @Autowired
    private lateinit var provider: ConfiguredLLMProvider

    @Autowired
    private lateinit var ollamaClient: OllamaClient

    @Autowired
    private lateinit var openAiClient: OpenAiClient

    @Autowired
    private lateinit var appConfig: AppConfigBean

    @Autowired
    private lateinit var profiledAdapters: List<ProfiledProviderAdapter>

    @Test
    fun `application consumes provider module beans with application adapters`() {
        assertNotNull(profiledAdapters.singleOrNull { it.adapter == ProviderAdapter.CODEX_CLI })
        assertNotNull(provider)
        assertSame(appConfig, injectedAppConfig(ollamaClient))
        assertSame(appConfig, injectedAppConfig(openAiClient))
    }

    private fun injectedAppConfig(bean: Any): Any? {
        val field = bean.javaClass.getDeclaredField("appConfig")
        field.isAccessible = true
        return field.get(bean)
    }
}
