package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.provider.ProviderWorkingDirectory
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

/** Verifies parsing of the machine-readable model catalog emitted by the Codex CLI. */
class CodexCliModelCatalogTest {
    @Test
    fun `keeps visible models and their display names`() {
        val catalog =
            CodexCliModelCatalog(
                mockk(),
                mockk(),
                ProviderWorkingDirectory { Path.of(".") },
            )

        val models =
            catalog.parse(
                """{"models":[{"slug":"visible","display_name":"Visible","visibility":"list"},{"slug":"hidden","display_name":"Hidden","visibility":"hide"}]}""",
            )

        assertEquals(listOf("visible"), models.map { it.id })
        assertEquals("Visible", models.single().name)
    }
}
