package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for model-visible access to packaged use-case documents.
 */
class UseCaseToolTest {
    /**
     * Ensures the model can discover documented Visual Agent workflows.
     */
    @Test
    fun `list returns packaged use case index`() {
        val tool = UseCaseTool()

        val result = tool.execute("""{"action":"list","limit":5}""")

        assertTrue(result.success)
        assertEquals("usecases", result.toolId)
        assertTrue(result.content.contains("Visual Agent Use Cases"))
        assertTrue(result.content.contains("UC-0000001"))
    }

    /**
     * Ensures a single use-case document can be fetched by stable ID.
     */
    @Test
    fun `show returns use case by id`() {
        val tool = UseCaseTool()

        val result = tool.execute("""{"action":"show","id":"UC-0000067"}""")

        assertTrue(result.success)
        assertTrue(result.content.contains("Query Use Case Catalog"))
        assertTrue(result.content.contains("de.heckenmann.visualagent.agent.tools.UseCaseTool"))
    }

    /**
     * Ensures the catalog can be searched by content.
     */
    @Test
    fun `search returns matching use cases`() {
        val tool = UseCaseTool()

        val result = tool.execute("""{"action":"search","query":"button","limit":10}""")

        assertTrue(result.success)
        assertTrue(result.content.contains("Use Case Search Results"))
        assertTrue(result.content.contains("UC-"))
    }

    /**
     * Ensures external paths cannot be used as catalog file names.
     */
    @Test
    fun `show rejects invalid file name`() {
        val tool = UseCaseTool()

        val result = tool.execute("""{"action":"show","file":"../secret.md"}""")

        assertTrue(!result.success)
        assertEquals("usecases", result.toolId)
        assertTrue(result.error?.contains("Unknown use case") == true)
    }
}
