@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import java.util.UUID

internal data class ProviderProfileFormState(
    val id: String = "",
    val name: String = "",
    val adapter: ProviderAdapter = ProviderAdapter.OPENAI_COMPATIBLE,
    val baseUrl: String = "",
    val apiKey: String = "",
    val enabled: Boolean = true,
    val defaultModel: String = "",
    val optionsText: String = "",
    val modelsText: String = "",
    val whitelistText: String = "",
    val blacklistText: String = "",
)

internal fun ProviderProfile.toFormState(): ProviderProfileFormState =
    ProviderProfileFormState(
        id = id,
        name = name,
        adapter = adapter,
        baseUrl = baseUrl,
        apiKey = apiKey,
        enabled = enabled,
        defaultModel = defaultModel,
        optionsText = options.toSettingsMapText(),
        modelsText = models.toProviderModelsText(),
        whitelistText = modelWhitelist.toCsvText(),
        blacklistText = modelBlacklist.toCsvText(),
    )

internal fun newProviderFormState(): ProviderProfileFormState =
    ProviderProfileFormState(
        id = "provider-${UUID.randomUUID()}",
        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com",
        enabled = true,
    )

internal fun ProviderProfileFormState.validationError(): String? =
    when {
        id.isBlank() -> "Provider ID is required."
        !id.trim().matches(PROVIDER_ID_PATTERN) -> "Provider ID contains invalid characters."
        name.isBlank() -> "Name is required."
        adapter != ProviderAdapter.CODEX_CLI && baseUrl.isBlank() -> "Base URL is required."
        else -> null
    }

internal fun ProviderProfileFormState.toProviderProfile(existing: ProviderProfile? = null): ProviderProfile =
    ProviderProfile(
        id = existing?.id ?: id.trim(),
        name = name.trim(),
        adapter = adapter,
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        enabled = enabled,
        defaultModel = defaultModel.trim(),
        options = optionsText.toSettingsMap(),
        models = modelsText.toProviderModels(),
        modelWhitelist = whitelistText.toCsvSet(),
        modelBlacklist = blacklistText.toCsvSet(),
    )

internal fun Map<String, String>.toSettingsMapText(): String =
    entries
        .sortedBy { it.key }
        .joinToString("\n") { (key, value) -> "$key=$value" }

internal fun String.toSettingsMap(): Map<String, String> =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { it.contains('=') }
        .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
        .filterKeys(String::isNotBlank)

internal fun List<ProviderModelConfig>.toProviderModelsText(): String =
    joinToString("\n") { model ->
        listOf(
            model.id,
            model.status.name,
            model.contextLimit?.toString().orEmpty(),
            model.outputLimit?.toString().orEmpty(),
            model.options.toSettingsOptionText(";"),
        ).joinToString("|")
    }

internal fun String.toProviderModels(): List<ProviderModelConfig> =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { line ->
            val parts = line.split('|', limit = 5)
            val id = parts.firstOrNull()?.trim().orEmpty()
            if (id.isBlank()) return@mapNotNull null
            ProviderModelConfig(
                id = id,
                status =
                    parts
                        .getOrNull(1)
                        ?.trim()
                        ?.uppercase()
                        ?.let { runCatching { ModelStatus.valueOf(it) }.getOrNull() }
                        ?: ModelStatus.ACTIVE,
                contextLimit = parts.getOrNull(2)?.trim()?.toIntOrNull(),
                outputLimit = parts.getOrNull(3)?.trim()?.toIntOrNull(),
                options =
                    parts
                        .getOrNull(4)
                        .orEmpty()
                        .split(';')
                        .map(String::trim)
                        .filter { it.contains('=') }
                        .associate { option ->
                            option.substringBefore('=').trim() to option.substringAfter('=').trim()
                        }.filterKeys(String::isNotBlank),
            )
        }.toList()

internal fun Set<String>.toCsvText(): String = sorted().joinToString(",")

internal fun String.toCsvSet(): Set<String> =
    split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

internal fun List<String>.toFavoriteModelText(): String =
    distinct()
        .filter(String::isNotBlank)
        .joinToString(",")

internal fun String.toFavoriteModelSet(): Set<String> = toCsvSet()

internal fun ShowResponse.toModelDetailsText(): String =
    buildString {
        appendLine("Model: $model")
        appendLine("Modified: ${modifiedAt.ifBlank { "unknown" }}")
        details?.let { metadata ->
            appendLine("Family: ${metadata.family ?: "unknown"}")
            appendLine("Size: ${metadata.parameterSize ?: "unknown"}")
            appendLine("Format: ${metadata.format ?: "unknown"}")
            appendLine("Quantization: ${metadata.quantizationLevel ?: "unknown"}")
        }
    }.trimEnd()

internal fun saveSessionSettings(
    config: AppConfigBean,
    providerCatalog: ProviderCatalogService,
    providerId: String,
    modelId: String,
) {
    val profile = providerCatalog.getProvider(providerId) ?: error("Provider is missing: $providerId")
    val selectedModelId = modelId.trim()
    providerCatalog.setActiveSelection(providerId, selectedModelId)
    mirrorProviderToAppConfig(config, profile, selectedModelId)
}

/**
 * Immediately activates a provider/model selection made in the settings UI.
 *
 * A provider without an available model is still activated so it cannot silently
 * fall back to a different provider. The main agent will report that a model must
 * be selected before it can send a request.
 */
internal fun activateMainAgentSelection(
    config: AppConfigBean,
    providerCatalog: ProviderCatalogService,
    providerId: String,
    modelId: String,
) {
    val profile = providerCatalog.getProvider(providerId) ?: error("Provider is missing: $providerId")
    val selectedModelId = modelId.trim()
    if (selectedModelId.isBlank()) {
        providerCatalog.setActiveProvider(providerId)
        mirrorProviderToAppConfig(config, profile)
    } else {
        saveSessionSettings(config, providerCatalog, providerId, selectedModelId)
    }
    config.save()
}

/**
 * Activates a selected provider with its configured default or first selectable model.
 *
 * @return The provider display name for the settings status message
 */
internal fun activateSelectedProvider(
    config: AppConfigBean,
    providerCatalog: ProviderCatalogService,
    providerId: String,
): String {
    val profile = providerCatalog.getProvider(providerId)
    val selectableModels = providerCatalog.selectableModels(providerId)
    val selectedModel =
        profile
            ?.defaultModel
            ?.takeIf { default -> selectableModels.any { it.id == default } }
            ?: selectableModels.firstOrNull()?.id.orEmpty()
    activateMainAgentSelection(config, providerCatalog, providerId, selectedModel)
    return profile?.name ?: providerId
}

internal fun mirrorProviderToAppConfig(
    config: AppConfigBean,
    profile: ProviderProfile,
    modelId: String = profile.defaultModel,
) {
    config.llmProvider = profile.id
    when (profile.id) {
        "ollama" -> {
            config.ollamaLocalUrl = profile.baseUrl
            config.ollamaApiKey = profile.apiKey
            config.ollamaModel = modelId
        }
        "openai" -> {
            config.openAiBaseUrl = profile.baseUrl
            config.openAiApiKey = profile.apiKey
            config.openAiModel = modelId
        }
    }
}

/**
 * Provider-backed state used to refresh the settings form.
 */
internal data class RefreshedProviderSettings(
    val providerProfiles: List<ProviderProfile>,
    val providers: List<ProviderProfile>,
    val providerId: String,
    val selectableModels: List<ProviderModelConfig>,
    val modelId: String,
)

/**
 * Reads the selected provider and its available model state for the settings form.
 */
internal fun refreshedProviderSettings(
    providerCatalog: ProviderCatalogService,
    selectedProviderId: String,
): RefreshedProviderSettings {
    val providerProfiles = providerCatalog.listProviders()
    val providers = providerCatalog.enabledProviders()
    val providerId = selectedProviderId.takeIf { id -> providers.any { it.id == id } } ?: providers.firstOrNull()?.id.orEmpty()
    val profile = providerCatalog.getProvider(providerId)
    val selectableModels = providerCatalog.selectableModels(providerId)
    val selectableModelIds = selectableModels.map(ProviderModelConfig::id)
    val modelId =
        providerCatalog
            .activeModelId()
            .takeIf { providerId == providerCatalog.activeProviderId() && it in selectableModelIds }
            ?: profile?.defaultModel?.takeIf { it in selectableModelIds }
            ?: selectableModels.firstOrNull()?.id.orEmpty()
    return RefreshedProviderSettings(providerProfiles, providers, providerId, selectableModels, modelId)
}

/**
 * Filters provider models according to the current search and favorites settings.
 */
internal fun filteredProviderModels(
    models: List<ProviderModelConfig>,
    search: String,
    favoritesOnly: Boolean,
    favoriteModels: Set<String>,
): List<ProviderModelConfig> =
    models.filter { model ->
        val matchesSearch = model.id.contains(search, ignoreCase = true) || model.name.contains(search, ignoreCase = true)
        val matchesFavorite = !favoritesOnly || model.id in favoriteModels
        matchesSearch && matchesFavorite
    }

/**
 * Returns whether all values required by the explicit settings save action are valid.
 */
internal fun canSaveSettings(
    hasActiveProvider: Boolean,
    modelId: String,
    loadLimit: Int?,
    maxParallelSubAgents: Int?,
    timeoutSeconds: Int?,
): Boolean =
    hasActiveProvider &&
        modelId.isNotBlank() &&
        loadLimit != null &&
        maxParallelSubAgents != null &&
        timeoutSeconds != null

internal fun Int.clampFontSize(): Int = coerceIn(MIN_SETTINGS_FONT_SIZE, MAX_SETTINGS_FONT_SIZE)

internal fun String.toBoundedIntOrNull(
    min: Int,
    max: Int,
): Int? = toIntOrNull()?.coerceIn(min, max)

private fun Map<String, String>.toSettingsOptionText(separator: String): String =
    entries
        .sortedBy { it.key }
        .joinToString(separator) { (key, value) -> "$key=$value" }

internal const val MIN_SETTINGS_FONT_SIZE = 10
internal const val MAX_SETTINGS_FONT_SIZE = 24
internal const val MIN_CONTEXT_LENGTH = 1024
internal const val MAX_CONTEXT_LENGTH = 32768
internal const val MIN_LOAD_LIMIT = 1
internal const val MAX_LOAD_LIMIT = 1000
internal const val MIN_PARALLEL_SUB_AGENTS = 1
internal const val MAX_PARALLEL_SUB_AGENTS = 20
internal const val MIN_TIMEOUT_SECONDS = 5
internal const val MAX_TIMEOUT_SECONDS = 600

private val PROVIDER_ID_PATTERN = Regex("[a-zA-Z0-9._-]+")
