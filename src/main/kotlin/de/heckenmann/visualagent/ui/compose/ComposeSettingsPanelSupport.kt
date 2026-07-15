package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean

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
        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com",
        enabled = true,
    )

internal fun ProviderProfileFormState.validationError(): String? =
    when {
        id.isBlank() -> "Provider ID is required."
        !id.trim().matches(PROVIDER_ID_PATTERN) -> "Provider ID contains invalid characters."
        name.isBlank() -> "Name is required."
        baseUrl.isBlank() -> "Base URL is required."
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
    baseUrl: String,
    apiKey: String,
) {
    providerCatalog.setActiveProvider(providerId)
    val profile = providerCatalog.getProvider(providerId) ?: error("Provider is missing: $providerId")
    val updated =
        profile.copy(
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            defaultModel = modelId.trim(),
        )
    providerCatalog.saveProvider(updated)
    mirrorProviderToAppConfig(config, updated)
}

internal fun mirrorProviderToAppConfig(
    config: AppConfigBean,
    profile: ProviderProfile,
) {
    config.llmProvider = profile.id
    when (profile.id) {
        "ollama" -> {
            config.ollamaLocalUrl = profile.baseUrl
            config.ollamaApiKey = profile.apiKey
            config.ollamaModel = profile.defaultModel
        }
        "openai" -> {
            config.openAiBaseUrl = profile.baseUrl
            config.openAiApiKey = profile.apiKey
            config.openAiModel = profile.defaultModel
        }
    }
}

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
