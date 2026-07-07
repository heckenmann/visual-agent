package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [visualAgentTypography], [draculaColorScheme], and [backgroundBrush].
 */
class ComposeWorkspaceThemeTest {
    @Test
    fun `dracula color scheme exposes signature palette colors`() {
        val scheme = draculaColorScheme()

        assertEquals(Color(0xFF1E1F29), scheme.background)
        assertEquals(Color(0xFF282A36), scheme.surface)
        assertEquals(Color(0xFFBD93F9), scheme.primary)
        assertEquals(Color(0xFFFF79C6), scheme.secondary)
        assertEquals(Color(0xFF8BE9FD), scheme.tertiary)
    }

    @Test
    fun `background brush returns different instances`() {
        val brush1 = backgroundBrush()
        val brush2 = backgroundBrush()

        // Each call creates a new Brush instance
        assertTrue(brush1 !== brush2)
    }

    @Test
    fun `typography scales font sizes relative to default`() {
        val typography = visualAgentTypography(28)

        // 28 is clamped to 24 -> scale = 24 / 14
        val body = typography.bodyLarge.fontSize
        val default = Typography().bodyLarge.fontSize
        assertTrue(body.value > default.value)
    }

    @Test
    fun `typography handles minimum font size`() {
        val typography = visualAgentTypography(1)

        val body = typography.bodyLarge.fontSize
        val default = Typography().bodyLarge.fontSize
        // scale = 10 / 14 -> smaller than default
        assertTrue(body.value < default.value)
    }
}
