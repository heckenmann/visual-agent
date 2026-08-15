package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.ThemeMode
import org.springframework.stereotype.Component
import de.heckenmann.visualagent.config.ThemeMode as ApplicationThemeMode

/** Maps the persisted application settings to the neutral [SettingsPort]. */
@Component
class SpringSettingsPort(
    private val config: AppConfigBean,
) : SettingsPort {
    override fun snapshot(): SettingsSnapshot = config.toProtocol()

    override fun save(settings: SettingsSnapshot) {
        config.apply {
            llmProvider = settings.providerId
            setActiveModel(settings.modelId)
            uiThemeMode = settings.uiThemeMode.toApplication()
            fontSize = settings.fontSize
            showPanelLabels = settings.showPanelLabels
            contextLength = settings.contextLength
            streamingEnabled = settings.streamingEnabled
            thinkingEnabled = settings.thinkingEnabled
            autoCompactionEnabled = settings.autoCompactionEnabled
            loadLimit = settings.loadLimit
            maxParallelSubAgents = settings.maxParallelSubAgents
            timeoutSeconds = settings.timeoutSeconds
            userModelInstruction = settings.userModelInstruction
            favoriteModels = settings.favoriteModels.joinToString(",")
            queueFlushMode = settings.queueFlushMode
            save()
        }
    }

    override fun addChangeListener(listener: (SettingsSnapshot) -> Unit): AutoCloseable = config.addChangeListener { listener(snapshot()) }
}

private fun AppConfigBean.toProtocol(): SettingsSnapshot =
    SettingsSnapshot(
        providerId = llmProvider,
        modelId = activeModel(),
        uiThemeMode = uiThemeMode.toProtocol(),
        fontSize = fontSize,
        showPanelLabels = showPanelLabels,
        contextLength = contextLength,
        streamingEnabled = streamingEnabled,
        thinkingEnabled = thinkingEnabled,
        autoCompactionEnabled = autoCompactionEnabled,
        loadLimit = loadLimit,
        maxParallelSubAgents = maxParallelSubAgents,
        timeoutSeconds = timeoutSeconds,
        userModelInstruction = userModelInstruction,
        favoriteModels = favoriteModels.split(',').map(String::trim).filter(String::isNotBlank),
        queueFlushMode = queueFlushMode,
    )

private fun ApplicationThemeMode.toProtocol(): ThemeMode = ThemeMode.valueOf(name)

private fun ThemeMode.toApplication(): ApplicationThemeMode = ApplicationThemeMode.valueOf(name)
