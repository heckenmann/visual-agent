@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Renders text with a progressive typewriter effect, revealing content
 * character-by-character or in small groups for a natural streaming feel.
 *
 * When [animate] is false the full [text] is displayed immediately.
 * When [animate] is true, the text is revealed incrementally. New text
 * that extends the previous text continues the animation from the current
 * position instead of restarting, so rapid streaming updates are visible.
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
    val currentText by rememberUpdatedState(text)
    val currentAnimate by rememberUpdatedState(animate)

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(tickDelayMs)
            if (!currentAnimate) {
                visibleLength = currentText.length
                continue
            }
            if (visibleLength < currentText.length) {
                visibleLength = (visibleLength + charsPerTick).coerceAtMost(currentText.length)
            }
        }
    }

    content(text.take(visibleLength))
}
