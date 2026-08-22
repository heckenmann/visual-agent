@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

private const val WORKSPACE_PANEL_ANIMATION_DURATION_MILLIS = 220

/** Returns the standard timing used by workspace panel transitions. */
internal fun <T> workspacePanelAnimationSpec() =
    tween<T>(
        durationMillis = WORKSPACE_PANEL_ANIMATION_DURATION_MILLIS,
        easing = FastOutSlowInEasing,
    )

private fun workspacePanelEnterTransition() =
    fadeIn(animationSpec = workspacePanelAnimationSpec()) +
        expandHorizontally(
            animationSpec = workspacePanelAnimationSpec(),
            expandFrom = Alignment.Start,
        )

private fun workspacePanelExitTransition() =
    fadeOut(animationSpec = workspacePanelAnimationSpec()) +
        shrinkHorizontally(
            animationSpec = workspacePanelAnimationSpec(),
            shrinkTowards = Alignment.Start,
        )

/**
 * Animates a workspace panel while it is added to or removed from the horizontal row.
 *
 * @param visible Whether the panel should be displayed
 * @param content Panel content
 */
@Composable
internal fun WorkspacePanelVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = workspacePanelEnterTransition(),
        exit = workspacePanelExitTransition(),
        label = "workspace panel visibility",
    ) {
        content()
    }
}
