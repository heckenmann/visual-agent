package de.heckenmann.visualagent.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.heckenmann.visualagent.protocol.ProviderPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Resolves the selected model's context limit and reports it to the staged settings. */
@Composable
internal fun modelContextLimitEffect(
    loaded: Boolean,
    providerId: String,
    modelId: String,
    catalogLimit: Int?,
    selectionRevision: Int,
    adoptLimit: Boolean,
    providerPort: ProviderPort,
    onLimitResolved: (Int?) -> Unit,
    onLimitApplied: (Int, Boolean) -> Unit,
    onSelectionResolved: (Int) -> Unit,
) {
    LaunchedEffect(loaded, providerId, modelId, catalogLimit, selectionRevision) {
        if (!loaded) return@LaunchedEffect
        val knownLimit = catalogLimit?.takeIf { it > 0 }
        onLimitResolved(knownLimit)
        if (knownLimit != null) {
            onLimitApplied(knownLimit, adoptLimit)
            onSelectionResolved(selectionRevision)
            return@LaunchedEffect
        }
        if (providerId.isBlank() || modelId.isBlank()) {
            onSelectionResolved(selectionRevision)
            return@LaunchedEffect
        }

        val detailsLimit =
            withContext(Dispatchers.IO) {
                runCatching { providerPort.modelDetails(providerId, modelId).contextLimit }.getOrNull()
            }?.takeIf { it > 0 }
        currentCoroutineContext().ensureActive()
        if (detailsLimit != null) {
            onLimitResolved(detailsLimit)
            onLimitApplied(detailsLimit, adoptLimit)
        }
        onSelectionResolved(selectionRevision)
    }
}
