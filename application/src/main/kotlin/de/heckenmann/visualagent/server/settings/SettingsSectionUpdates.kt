package de.heckenmann.visualagent.server.settings

import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore
import de.heckenmann.visualagent.protocol.SettingsSection
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.server.validation.validateInterpolationSelection

/** Applies only owned fields to the live configuration; never replay a stale full UI snapshot. */
internal fun AppConfigBean.applySettingsSection(
    section: SettingsSection,
    settings: SettingsSnapshot,
    catalog: ProviderCatalogService,
    memory: MainAgentLongTermMemoryStore,
) {
    when (section) {
        SettingsSection.APPEARANCE -> {
            uiThemeMode =
                de.heckenmann.visualagent.config.ThemeMode
                    .valueOf(settings.uiThemeMode.name)
            fontSize = settings.fontSize
            uiScalePercent = settings.uiScalePercent?.coerceIn(50..200)
            showPanelLabels = settings.showPanelLabels
        }
        SettingsSection.CONVERSATION -> {
            require(settings.promptInterpolationProviderId.isBlank() == settings.promptInterpolationModelId.isBlank()) {
                "Choose an interpolation provider and model together"
            }
            require(settings.promptInterpolationContextMessages in 5..10)
            require(settings.followUpSuggestionCount in AppConfigBean.FOLLOW_UP_SUGGESTION_COUNT_RANGE)
            require(settings.followUpSuggestionIdleDelaySeconds in AppConfigBean.FOLLOW_UP_SUGGESTION_DELAY_RANGE)
            if (settings.promptInterpolationProviderId.isNotBlank()) {
                validateInterpolationSelection(
                    catalog.listProviders(),
                    settings.promptInterpolationProviderId,
                    settings.promptInterpolationModelId,
                )
            }
            promptInterpolationProviderId = settings.promptInterpolationProviderId
            promptInterpolationModelId = settings.promptInterpolationModelId
            automaticPromptInterpolation = settings.automaticPromptInterpolation
            promptInterpolationContextMessages = settings.promptInterpolationContextMessages
            followUpSuggestionsEnabled = settings.followUpSuggestionsEnabled
            followUpSuggestionIdleDelaySeconds = settings.followUpSuggestionIdleDelaySeconds
            followUpSuggestionCount = settings.followUpSuggestionCount
            queueFlushMode = settings.queueFlushMode
        }
        SettingsSection.AGENT -> {
            require(settings.maxMainAgentMemoryChars in AppConfigBean.MAIN_AGENT_MEMORY_CHARS_RANGE)
            require(
                settings.maxMainAgentMemoryChars >= memory.snapshot().contentLength,
            ) { "Reduce the memory document before lowering its limit" }
            userModelInstruction = settings.userModelInstruction
            loadLimit = settings.loadLimit
            maxParallelSubAgents = settings.maxParallelSubAgents
            timeoutSeconds = settings.timeoutSeconds
            maxMainAgentMemoryChars = settings.maxMainAgentMemoryChars
        }
        SettingsSection.MODEL -> {
            require(settings.contextLength > 0)
            check(catalog.activeProviderId() == settings.providerId && catalog.activeModelId() == settings.modelId) {
                "The active model changed. Reopen model settings before saving its context budget."
            }
            val limit = catalog.selectableModels(settings.providerId).firstOrNull { it.id == settings.modelId }?.contextLimit
            require(
                limit == null || limit <= 0 || settings.contextLength <= limit,
            ) { "Context budget exceeds the model's reported capacity" }
            contextLength = settings.contextLength
        }
        SettingsSection.UPDATES -> {
            automaticUpdatesEnabled = settings.automaticUpdatesEnabled
            includePrereleaseUpdates = settings.includePrereleaseUpdates
        }
    }
    saveFields(section.preferenceKeys())
}

private fun SettingsSection.preferenceKeys(): Set<String> =
    when (this) {
        SettingsSection.APPEARANCE ->
            setOf(
                AppConfigBean.KEY_UI_THEME_MODE,
                AppConfigBean.KEY_UI_FONT_SIZE,
                AppConfigBean.KEY_UI_SCALE_PERCENT,
                AppConfigBean.KEY_UI_SHOW_PANEL_LABELS,
            )
        SettingsSection.CONVERSATION ->
            setOf(
                AppConfigBean.KEY_PROMPT_INTERPOLATION_PROVIDER,
                AppConfigBean.KEY_PROMPT_INTERPOLATION_MODEL,
                AppConfigBean.KEY_AUTOMATIC_PROMPT_INTERPOLATION,
                AppConfigBean.KEY_PROMPT_INTERPOLATION_CONTEXT_MESSAGES,
                AppConfigBean.KEY_FOLLOW_UP_SUGGESTIONS_ENABLED,
                AppConfigBean.KEY_FOLLOW_UP_SUGGESTION_DELAY_SECONDS,
                AppConfigBean.KEY_FOLLOW_UP_SUGGESTION_COUNT,
                AppConfigBean.KEY_SESSION_QUEUE_FLUSH_MODE,
            )
        SettingsSection.AGENT ->
            setOf(
                AppConfigBean.KEY_SESSION_USER_MODEL_INSTRUCTION,
                AppConfigBean.KEY_SESSION_LOAD_LIMIT,
                AppConfigBean.KEY_SESSION_MAX_PARALLEL_SUB_AGENTS,
                AppConfigBean.KEY_SESSION_TIMEOUT_SECONDS,
                AppConfigBean.KEY_MAIN_AGENT_MEMORY_CHARS,
            )
        SettingsSection.MODEL -> setOf(AppConfigBean.KEY_SESSION_CONTEXT_LENGTH)
        SettingsSection.UPDATES -> setOf(AppConfigBean.KEY_AUTOMATIC_UPDATES_ENABLED, AppConfigBean.KEY_INCLUDE_PRERELEASE_UPDATES)
    }
