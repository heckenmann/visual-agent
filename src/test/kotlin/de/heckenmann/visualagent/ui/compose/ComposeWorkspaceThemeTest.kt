package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import de.heckenmann.visualagent.config.ThemeMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [visualAgentLightColorScheme], [visualAgentDarkColorScheme],
 * [isSystemInDarkTheme], and [visualAgentTypography].
 */
class ComposeWorkspaceThemeTest {
    @Test
    fun `light and dark baseline schemes expose different surface colors`() {
        val light = visualAgentLightColorScheme()
        val dark = visualAgentDarkColorScheme()

        assertNotEquals(light.surface, dark.surface)
        assertNotEquals(light.background, dark.background)
    }

    @Test
    fun `baseline schemes define primary, onPrimary and error roles`() {
        val light = visualAgentLightColorScheme()
        val dark = visualAgentDarkColorScheme()

        assertNotEquals(Color.Unspecified, light.primary)
        assertNotEquals(Color.Unspecified, light.onPrimary)
        assertNotEquals(Color.Unspecified, light.error)
        assertNotEquals(Color.Unspecified, dark.primary)
        assertNotEquals(Color.Unspecified, dark.onPrimary)
        assertNotEquals(Color.Unspecified, dark.error)
    }

    @Test
    fun `isSystemInDarkTheme resolves explicit modes`() {
        assertFalse(isSystemInDarkTheme(ThemeMode.LIGHT))
        assertTrue(isSystemInDarkTheme(ThemeMode.DARK))
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
