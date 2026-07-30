@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Renders text with a progressive typewriter effect, revealing content
 * character-by-character or in small groups for a natural streaming feel.
 *
 * When [animate] is false the full [text] is displayed immediately.
 * When [animate] is true, the text is revealed incrementally — each
 * change to [text] restarts the animation from the beginning.
 *
 * @param text the full text to display
 * @param animate whether to animate the reveal
 * @param charsPerTick how many characters to reveal per animation tick
 * @param tickDelayMs delay in milliseconds between ticks
 * @param content composable receiving the currently visible portion of text
 */
@Composable
internal fun StreamingText(
    text: String,
    animate: Boolean = true,
    charsPerTick: Int = 3,
    tickDelayMs: Long = 16,
    content: @Composable (displayedText: String) -> Unit,
) {
    var visibleLength by remember { mutableIntStateOf(if (animate) 0 else text.length) }

    LaunchedEffect(text, animate) {
        if (!animate) {
            visibleLength = text.length
            return@LaunchedEffect
        }
        // Restart animation from the beginning whenever text changes.
        visibleLength = 0
        while (visibleLength < text.length) {
            delay(tickDelayMs)
            visibleLength = (visibleLength + charsPerTick).coerceAtMost(text.length)
        }
    }

    content(text.take(visibleLength))
}
