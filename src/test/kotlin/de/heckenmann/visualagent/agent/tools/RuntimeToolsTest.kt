package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeToolsTest {
    @Test
    fun `terminal returns output and exit status`() {
        val tool = TerminalTool()

        val success = tool.execute("""{"command":"printf hello"}""")
        val failure = tool.execute("""{"command":"printf failed; exit 7"}""")

        assertTrue(success.success)
        assertEquals("hello", success.content)
        assertFalse(failure.success)
        assertEquals("Exit 7", failure.error)
        assertEquals("failed", failure.content)
    }

    @Test
    fun `placeholder runtime tools return structured failures`() {
        assertFalse(BrowserTool().execute("{}").success)
        assertFalse(SearchTool().execute("{}").success)
    }

    @Test
    fun `sleep accepts zero seconds`() {
        val result = SleepTool().execute("""{"seconds":0}""")

        assertTrue(result.success)
        assertEquals("slept 0s", result.content)
    }
}
