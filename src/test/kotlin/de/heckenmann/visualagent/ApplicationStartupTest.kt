package de.heckenmann.visualagent

import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import kotlin.test.assertTrue

class ApplicationStartupTest {
    @Test
    fun `application context starts and closes`() {
        val context =
            SpringApplicationBuilder(VisualAgentApplication::class.java)
                .web(WebApplicationType.NONE)
                .properties(
                    "visual-agent.ui.enabled=false",
                    "spring.main.lazy-initialization=true",
                ).run()
        try {
            assertTrue(context.isActive)
        } finally {
            context.close()
        }
        assertTrue(!context.isActive)
    }
}
