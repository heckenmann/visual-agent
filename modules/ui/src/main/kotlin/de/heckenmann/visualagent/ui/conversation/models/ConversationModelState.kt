package de.heckenmann.visualagent.ui.conversation.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Composition-owned projection of the authoritative server model selection and catalog. */
internal class ConversationModelState(
    private val providers: ProviderPort,
    private val settings: SettingsPort,
    private val scope: CoroutineScope,
) {
    var profiles by mutableStateOf(emptyList<ProviderProfile>())
        private set
    var selected by mutableStateOf(SettingsSnapshot())
        private set
    var loaded by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var generation = 0
    private var reload: Job? = null

    fun changed() {
        if (busy) return
        error = null
        reload?.cancel()
        reload = scope.launch { load() }
    }

    private suspend fun load() {
        val revision = ++generation
        try {
            val next = withContext(Dispatchers.IO) { providers.listProviders() to settings.snapshot() }
            currentCoroutineContext().ensureActive()
            if (revision == generation) {
                profiles = next.first
                selected = next.second
                loaded = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (revision == generation) error = failure.toUiErrorMessage()
        }
    }

    fun select(
        providerId: String,
        modelId: String,
    ) {
        if (busy) return
        perform {
            if (providerId != selected.providerId) providers.refreshModels(providerId)
            settings.selectConversationModel(providerId, modelId)
        }
    }

    fun refresh(providerId: String) {
        if (!busy) perform { providers.refreshModels(providerId) }
    }

    fun favorite(
        modelId: String,
        favorite: Boolean,
    ) {
        if (!busy) perform { settings.setModelFavorite(modelId, favorite) }
    }

    private fun perform(operation: suspend () -> Unit) {
        busy = true
        error = null
        generation++
        reload?.cancel()
        scope.launch {
            try {
                withContext(Dispatchers.IO) { operation() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.toUiErrorMessage()
            } finally {
                busy = false
                load()
            }
        }
    }
}

/** Refreshes only on authoritative events, not scrolling, resizing or recomposition. */
@Composable
internal fun rememberConversationModelState(
    providers: ProviderPort,
    settings: SettingsPort,
): ConversationModelState {
    val scope = rememberCoroutineScope()
    val state = remember(providers, settings, scope) { ConversationModelState(providers, settings, scope) }
    DisposableEffect(providers, settings, state) {
        val catalog = providers.addChangeListener { scope.launch { state.changed() } }
        val preferences = settings.addChangeListener { scope.launch { state.changed() } }
        state.changed()
        onDispose {
            catalog.close()
            preferences.close()
        }
    }
    return state
}
