package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Gives the manual wizard Escape handling when an overlay does not own focus. */
@Composable
internal fun onboardingDismissOnEscape(
    enabled: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
): Modifier {
    if (!enabled) return Modifier
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) {
        withFrameNanos { }
        focus.requestFocus()
    }
    return Modifier
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                if (!busy) onDismiss()
                true
            } else {
                false
            }
        }.focusRequester(focus)
        .focusable()
}
