package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the built-in manual tool.
 */
class ManualToolTest {
    /**
     * Ensures that markdown documentation can be retrieved for the model.
     */
    @Test
    fun `show markdown returns markdown format reference`() {
        val tool = ManualTool()

        val result = tool.execute("""{"action":"show","topic":"markdown"}""")

        assertTrue(result.success)
        assertEquals("manual", result.toolId)
        assertTrue(result.content.contains("Markdown Quick Reference"))
        assertTrue(result.content.contains("CommonMark"))
        assertTrue(result.content.contains("```kotlin"))
    }

    /**
     * Ensures unknown manual topics return a structured error.
     */
    @Test
    fun `unknown topic returns failure with available topics`() {
        val tool = ManualTool()

        val result = tool.execute("""{"action":"show","topic":"does-not-exist"}""")

        assertTrue(!result.success)
        assertEquals("manual", result.toolId)
        assertTrue(result.error?.contains("Available topics") == true)
    }
}
