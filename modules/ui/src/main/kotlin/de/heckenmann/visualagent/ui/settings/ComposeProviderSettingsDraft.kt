package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.protocol.MainAgentMemorySnapshot
import de.heckenmann.visualagent.protocol.ModelStatus
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSection

/** Renders a revision-aware draft for the main agent's durable reference document. */
@Composable
internal fun mainAgentMemorySection(
    content: String,
    snapshot: MainAgentMemorySnapshot,
    limit: Int,
    onContentChange: (String) -> Unit,
) {
    val size = content.codePointCount(0, content.length)
    PanelSection(title = "Main-agent memory") {
        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("Durable model reference") },
            supportingText = { Text("Revision ${snapshot.revision} · $size / $limit characters") },
            isError = size > limit,
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        PanelInfoBox("The main agent receives this durable reference on every request. Do not store secrets or credentials here.")
    }
}

/** Creates the empty memory snapshot shown while the overlay is loading. */
internal fun initialMainAgentMemorySnapshot(): MainAgentMemorySnapshot =
    MainAgentMemorySnapshot(content = "", contentLength = 0, limit = 12_000, revision = 0)

/** Holds the complete local provider edit until the settings overlay is saved or reset. */
internal data class ProviderSettingsDraft(
    val providers: List<ProviderProfile> = emptyList(),
    val providerId: String = "",
    val modelId: String = "",
    val favoriteModels: Set<String> = emptySet(),
    val conversationSettings: SettingsSnapshot = SettingsSnapshot(),
)

/** Creates a staged provider draft from persisted settings and the provider catalog. */
internal fun providerSettingsDraft(
    settings: SettingsSnapshot,
    providers: List<ProviderProfile>,
): ProviderSettingsDraft =
    ProviderSettingsDraft(
        providers = providers,
        providerId = settings.providerId,
        modelId = settings.modelId,
        favoriteModels = settings.favoriteModels.toSet(),
        conversationSettings = settings,
    )

/** Replaces one staged profile and retains a valid enabled provider/model selection. */
internal fun ProviderSettingsDraft.upsert(profile: ProviderProfile): ProviderSettingsDraft =
    copy(providers = providers.filterNot { it.id == profile.id } + profile).normalizeSelection()

/** Removes a staged provider and retains a valid enabled provider/model selection. */
internal fun ProviderSettingsDraft.remove(providerId: String): ProviderSettingsDraft =
    copy(providers = providers.filterNot { it.id == providerId }).normalizeSelection()

/** Keeps the draft selection aligned with an enabled provider and one of its selectable models. */
internal fun ProviderSettingsDraft.normalizeSelection(): ProviderSettingsDraft {
    val nextProvider =
        providerId.takeIf { id -> providers.any { it.id == id && it.enabled } }
            ?: providers.firstOrNull(ProviderProfile::enabled)?.id.orEmpty()
    val selectableModels = providers.firstOrNull { it.id == nextProvider }?.selectableModels().orEmpty()
    val nextModel = modelId.takeIf { id -> selectableModels.any { it.id == id } } ?: selectableModels.firstOrNull()?.id.orEmpty()
    return copy(providerId = nextProvider, modelId = nextModel)
}

/** Replaces the discovered models for one staged provider and preserves a valid selection. */
internal fun ProviderSettingsDraft.withModels(
    providerId: String,
    models: List<ProviderModel>,
): ProviderSettingsDraft {
    val updated = providers.map { profile -> if (profile.id == providerId) profile.copy(models = models) else profile }
    val selectedModel = modelId.takeIf { id -> models.any { it.id == id } } ?: models.firstOrNull()?.id.orEmpty()
    return copy(providers = updated, modelId = selectedModel)
}

/** Returns models that are enabled by the profile's allow and block lists. */
internal fun ProviderProfile.selectableModels(): List<ProviderModel> =
    models.filter { model ->
        model.id !in modelBlacklist &&
            (modelWhitelist.isEmpty() || model.id in modelWhitelist) &&
            model.status != ModelStatus.DEPRECATED &&
            model.status != ModelStatus.DISABLED
    }
