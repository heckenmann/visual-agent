package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.SettingsPort
import kotlinx.coroutines.launch

/** Remembers and wires a suggestion controller to the server port and persisted settings. */
@Composable
internal fun rememberConversationSuggestionController(
    suggestionPort: ConversationSuggestionPort,
    settingsPort: SettingsPort,
): ConversationSuggestionController {
    val scope = rememberCoroutineScope()
    val controller = remember(suggestionPort) { ConversationSuggestionController(suggestionPort, scope) }
    DisposableEffect(suggestionPort) {
        val handle = suggestionPort.addCompletionListener { event -> scope.launch { controller.onCompletion(event) } }
        onDispose {
            handle.close()
            controller.close()
        }
    }
    DisposableEffect(settingsPort) {
        val handle = settingsPort.addChangeListener { snapshot -> scope.launch { controller.updateSettings(snapshot) } }
        onDispose { handle.close() }
    }
    LaunchedEffect(settingsPort) {
        controller.updateSettings(settingsPort.snapshotAsync())
    }
    return controller
}
