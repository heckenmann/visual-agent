package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
