package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.config.AppConfigBean
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeSettingsActionsTest {
    @Test
    fun `applyAndSaveSettings writes all values to config`() {
        val config = AppConfigBean()
        applyAndSaveSettings(
            config = config,
            fontSize = 16,
            contextLength = 8192,
            loadLimitValue = 100,
            maxParallelValue = 8,
            timeoutValue = 300,
            streamingEnabled = false,
            thinkingEnabled = true,
            autoCompactionEnabled = false,
            queueFlushMode = "ALL_AT_ONCE",
            userInstruction = "Be helpful",
        )
        assertEquals(16, config.fontSize)
        assertEquals(8192, config.contextLength)
        assertEquals(100, config.loadLimit)
        assertEquals(8, config.maxParallelSubAgents)
        assertEquals(300, config.timeoutSeconds)
        assertEquals(false, config.streamingEnabled)
        assertEquals(true, config.thinkingEnabled)
        assertEquals(false, config.autoCompactionEnabled)
        assertEquals("ALL_AT_ONCE", config.queueFlushMode)
        assertEquals("Be helpful", config.userModelInstruction)
    }

    @Test
    fun `applyAndSaveSettings uses fallbacks for null values`() {
        val config = AppConfigBean()
        config.loadLimit = 50
        config.maxParallelSubAgents = 4
        config.timeoutSeconds = 120
        applyAndSaveSettings(
            config = config,
            fontSize = 14,
            contextLength = 4096,
            loadLimitValue = null,
            maxParallelValue = null,
            timeoutValue = null,
            streamingEnabled = true,
            thinkingEnabled = false,
            autoCompactionEnabled = true,
            queueFlushMode = "ONE_BY_ONE",
            userInstruction = "",
        )
        assertEquals(50, config.loadLimit)
        assertEquals(4, config.maxParallelSubAgents)
        assertEquals(120, config.timeoutSeconds)
    }
}
