package de.heckenmann.visualagent.ui.application

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.ui.workspace.ModelCapabilityWarnings
import de.heckenmann.visualagent.ui.workspace.modelCapabilityWarnings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolves active-model warnings, refreshing the context limit when the provider exposes one. */
@Composable
internal fun rememberModelCapabilityWarnings(
    providers: ProviderPort,
    configuredContextLength: Int,
    providerRevision: Int,
    selectionProviderId: String,
    selectionModelId: String,
): ModelCapabilityWarnings {
    var reportedContextLimit by remember { mutableStateOf<Int?>(null) }
    val activeProvider =
        remember(providerRevision, selectionProviderId, selectionModelId) {
            providers.getProvider(providers.activeProviderId())
        }
    val activeModel = activeProvider?.models?.firstOrNull { it.id == providers.activeModelId() }
    LaunchedEffect(activeProvider?.id, activeModel?.id, providerRevision) {
        reportedContextLimit = activeModel?.contextLimit
        val providerId = activeProvider?.id ?: return@LaunchedEffect
        val modelId = activeModel?.id ?: return@LaunchedEffect
        reportedContextLimit =
            withContext(Dispatchers.IO) {
                runCatching { providers.modelDetails(providerId, modelId).contextLimit }.getOrNull()
            } ?: activeModel.contextLimit
    }
    return modelCapabilityWarnings(
        configuredContextLength,
        activeModel?.copy(contextLimit = reportedContextLimit ?: activeModel.contextLimit),
    )
}
