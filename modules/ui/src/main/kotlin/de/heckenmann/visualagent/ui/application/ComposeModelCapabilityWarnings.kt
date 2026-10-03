package de.heckenmann.visualagent.ui.application

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.ui.workspace.ModelCapabilityWarnings
import de.heckenmann.visualagent.ui.workspace.modelCapabilityWarnings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads active-model metadata off the UI thread only when the selection or catalog changes. */
@Composable
internal fun rememberModelCapabilityWarnings(
    providers: ProviderPort,
    configuredContextLength: Int,
    providerRevision: Int,
    selectionProviderId: String,
    selectionModelId: String,
): ModelCapabilityWarnings {
    var activeModel by remember(providers, selectionProviderId, selectionModelId) { mutableStateOf<ProviderModel?>(null) }
    LaunchedEffect(providers, providerRevision, selectionProviderId, selectionModelId) {
        val selection =
            withContext(Dispatchers.IO) {
                val providerId = providers.activeProviderId()
                val modelId = providers.activeModelId()
                val model = providers.getProvider(providerId)?.models?.firstOrNull { it.id == modelId }
                Triple(providerId, modelId, model)
            }
        activeModel = selection.third
        val model = selection.third ?: return@LaunchedEffect
        val limit =
            withContext(Dispatchers.IO) {
                runCatching { providers.modelDetails(selection.first, selection.second).contextLimit }.getOrNull()
            }
        activeModel = model.copy(contextLimit = limit ?: model.contextLimit)
    }
    return modelCapabilityWarnings(configuredContextLength, activeModel)
}
