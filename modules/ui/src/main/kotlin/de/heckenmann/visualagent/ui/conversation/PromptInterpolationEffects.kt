package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.SettingsPort

/** Cancels interpolation on disposal and suspends idle suggestions during preparation. */
@Composable
internal fun rememberPromptInterpolationController(
    conversation: ConversationPort,
    settings: SettingsPort,
    state: ConversationUiState,
    suggestions: ConversationSuggestionController,
    send: (String) -> Unit,
): PromptInterpolationController {
    val scope = rememberCoroutineScope()
    val latestSend by rememberUpdatedState(send)
    val controller =
        remember(conversation, settings, state) {
            PromptInterpolationController(
                conversation,
                settings,
                scope,
                { state.input },
                {
                    state.input = it
                    suggestions.onInputChanged(it)
                },
                { latestSend(it) },
                { state.status = it },
            )
        }
    val busy by controller.busy.collectAsState()
    LaunchedEffect(busy, state.sending) { suggestions.onSendingChanged(busy || state.sending) }
    DisposableEffect(controller) { onDispose { controller.cancel(report = false) } }
    return controller
}
