package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal fun draculaColorScheme() =
    darkColorScheme(
        primary = Color(0xFFBD93F9),
        secondary = Color(0xFFFF79C6),
        tertiary = Color(0xFF8BE9FD),
        background = Color(0xFF1E1F29),
        surface = Color(0xFF282A36),
        onPrimary = Color(0xFF191A21),
        onSecondary = Color(0xFF191A21),
        onTertiary = Color(0xFF191A21),
        onBackground = Color(0xFFF8F8F2),
        onSurface = Color(0xFFF8F8F2),
    )

internal fun backgroundBrush(): Brush =
    Brush.linearGradient(
        listOf(
            Color(0xFF111217),
            Color(0xFF202230),
            Color(0xFF2A2140),
        ),
    )

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
