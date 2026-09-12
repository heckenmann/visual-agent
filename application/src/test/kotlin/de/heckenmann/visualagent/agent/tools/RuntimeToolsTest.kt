package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeToolsTest {
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
