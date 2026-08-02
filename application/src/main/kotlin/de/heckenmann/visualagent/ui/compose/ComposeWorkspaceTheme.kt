@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.heckenmann.visualagent.config.ThemeMode

/**
 * Light Material3 color scheme for Visual Agent.
 *
 * Uses the baseline Material3 light color scheme without custom colors.
 */
internal fun visualAgentLightColorScheme(): ColorScheme = lightColorScheme()

/**
 * Dark Material3 color scheme for Visual Agent.
 *
 * Uses the baseline Material3 dark color scheme without custom colors.
 */
internal fun visualAgentDarkColorScheme(): ColorScheme = darkColorScheme()

/**
 * Resolves the effective dark-mode flag for the current platform.
 *
 * [ThemeMode.LIGHT] always returns false, [ThemeMode.DARK] always returns true,
 * and [ThemeMode.SYSTEM] queries the OS appearance. Detection uses native CLI or
 * registry commands and falls back to dark on failure so the app remains usable.
 *
 * @param mode Theme mode selected by the user
 * @return True when the effective UI should render in dark colors
 */
internal fun isSystemInDarkTheme(mode: ThemeMode): Boolean =
    when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> detectSystemDarkTheme()
    }

private fun detectSystemDarkTheme(): Boolean {
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    return when {
        os.contains("mac") -> detectMacOsDarkTheme()
        os.contains("win") -> detectWindowsDarkTheme()
        os.contains("nix") || os.contains("nux") -> detectGnomeDarkTheme()
        else -> true
    }
}

private fun detectMacOsDarkTheme(): Boolean {
    val output = runProcess("defaults", "read", "-g", "AppleInterfaceStyle") ?: return true
    return output.contains("Dark", ignoreCase = true)
}

private fun detectWindowsDarkTheme(): Boolean {
    val output =
        runProcess(
            "reg",
            "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v",
            "AppsUseLightTheme",
        ) ?: return true
    val match = Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)").find(output)
    return match
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull(16)
        ?.let { it == 0 }
        ?: true
}

private fun detectGnomeDarkTheme(): Boolean {
    val output = runProcess("gsettings", "get", "org.gnome.desktop.interface", "color-scheme") ?: return true
    return output.contains("dark", ignoreCase = true)
}

private fun runProcess(vararg command: String): String? =
    runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.inputStream
            .bufferedReader()
            .use { it.readText().trim() }
            .also { process.waitFor() }
            .takeIf { process.exitValue() == 0 }
    }.getOrNull()

/**
 * Scales the Material3 baseline typography by the user-selected font size.
 *
 * @param fontSize User-selected font size in pixels, coerced to the supported range
 * @return A [Typography] instance with all text styles scaled proportionally
 */
internal fun visualAgentTypography(fontSize: Int): Typography {
    val scale = fontSize.coerceIn(10, 24) / DEFAULT_FONT_SIZE.toFloat()
    val defaults = Typography()
    return defaults.copy(
        displayLarge = defaults.displayLarge.scaled(scale),
        displayMedium = defaults.displayMedium.scaled(scale),
        displaySmall = defaults.displaySmall.scaled(scale),
        headlineLarge = defaults.headlineLarge.scaled(scale),
        headlineMedium = defaults.headlineMedium.scaled(scale),
        headlineSmall = defaults.headlineSmall.scaled(scale),
        titleLarge = defaults.titleLarge.scaled(scale),
        titleMedium = defaults.titleMedium.scaled(scale),
        titleSmall = defaults.titleSmall.scaled(scale),
        bodyLarge = defaults.bodyLarge.scaled(scale),
        bodyMedium = defaults.bodyMedium.scaled(scale),
        bodySmall = defaults.bodySmall.scaled(scale),
        labelLarge = defaults.labelLarge.scaled(scale),
        labelMedium = defaults.labelMedium.scaled(scale),
        labelSmall = defaults.labelSmall.scaled(scale),
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle = copy(fontSize = fontSize.scaled(scale))

private fun TextUnit.scaled(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) {
        this
    } else {
        value.times(scale).sp
    }

private const val DEFAULT_FONT_SIZE = 14

/**
 * Converts a Compose [Color] to a `#RRGGBB` hex string.
 *
 * Alpha is ignored because the canvas persistence format stores opaque colors.
 *
 * @return Uppercase hex string including the leading hash
 */
internal fun Color.toHexString(): String {
    val rgb = toArgb() and 0xFFFFFF
    return "#%06X".format(rgb)
}
