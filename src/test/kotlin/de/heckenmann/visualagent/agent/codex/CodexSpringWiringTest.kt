package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.ConfiguredLLMProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertNotNull

@SpringBootTest(properties = ["visual-agent.ui.enabled=false"])
internal class CodexSpringWiringTest {
    @Autowired
    private lateinit var provider: ConfiguredLLMProvider

    @Test
    fun `configured provider receives codex cli provider`() {
        val field = ConfiguredLLMProvider::class.java.getDeclaredField("codexCliProvider")
        field.isAccessible = true

        assertNotNull(field.get(provider))
    }
}
