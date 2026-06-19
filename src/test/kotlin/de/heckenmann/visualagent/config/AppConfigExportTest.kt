package de.heckenmann.visualagent.config

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppConfigExportTest {
    @Test
    fun `configuration export contains session settings but excludes api key`() {
        val file = Files.createTempFile("visual-agent-config", ".properties").toFile()
        try {
            AppConfig.instance.exportTo(file)
            val properties = Properties().apply { file.inputStream().use(::load) }

            assertEquals(AppConfig.instance.maxParallelSubAgents.toString(), properties["session.max.parallel.sub.agents"])
            assertEquals(AppConfig.instance.theme, properties["ui.theme"])
            assertFalse(properties.containsKey("openai.api.key"))
            assertFalse(properties.containsKey("ollama.api.key"))
        } finally {
            file.delete()
        }
    }
}
