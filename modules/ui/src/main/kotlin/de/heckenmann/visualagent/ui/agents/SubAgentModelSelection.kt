package de.heckenmann.visualagent.ui.agents

import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.capabilitySupport
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.components.labelWithCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preserves the existing provider dropdown when advanced models are configured. */
internal fun subAgentProviderOptions(providerPort: ProviderPort): List<PanelSelectOption> =
    listOf(PanelSelectOption(INHERIT_SELECTION, "Inherit session provider")) +
        providerPort.enabledProviders().map { PanelSelectOption(it.id, "${it.name} (${it.id})") }

/** Model selections shared by the sub-agent editor and its regression tests. */
internal fun subAgentModelOptions(
    providerPort: ProviderPort,
    providerId: String,
    currentModel: String,
): List<PanelSelectOption> {
    if (providerId.isBlank()) {
        return listOf(PanelSelectOption(INHERIT_SELECTION, "Inherit session model"))
    }
    val catalogModels =
        providerPort
            .selectableModels(providerId)
            .filter { model ->
                model.capabilitySupport("chat") != CapabilitySupport.UNSUPPORTED
            }.map { model -> PanelSelectOption(model.id, model.labelWithCapabilities()) }
    val customModel =
        currentModel
            .takeIf { it.isNotBlank() && catalogModels.none { candidate -> candidate.value == it } }
            ?.let { PanelSelectOption(it, "Custom: $it") }
    return listOf(PanelSelectOption(INHERIT_SELECTION, "Inherit provider default")) +
        catalogModels +
        listOfNotNull(customModel)
}

/** A cancelled editor must not convert coroutine cancellation into a normal probe error. */
internal suspend fun probeSubAgentCapability(
    providerPort: ProviderPort,
    capability: String,
    selection: String,
): String {
    val providerId = selection.substringBefore("::")
    val modelId = selection.substringAfter("::")
    return try {
        withContext(Dispatchers.IO) {
            providerPort.probeModelCapability(providerId, modelId, capability)
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        "$capability model probe failed: ${error.message}"
    }
}
