@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Renders the waiting indicator above a fixed conversation input card.
 *
 * The caller renders this after the input card so the indicator remains in the
 * topmost panel layer rather than being covered by the fixed composer.
 *
 * @param visible Whether canonical in-flight activity requires the indicator
 * @param bottomPadding Space reserved for the fixed input card
 * @param modifier Modifier used to position the overlay within the conversation panel
 */
@Composable
internal fun ConversationWaitingOverlay(
    visible: Boolean,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        ConversationWaitingIndicator(
            modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding),
        )
    }
}
