@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.settings

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

/**
 * Applies settings values to the config bean and persists them.
 */
internal fun applyAndSaveSettings(
    config: AppConfigBean,
    fontSize: Int,
    contextLength: Int,
    loadLimitValue: Int?,
    maxParallelValue: Int?,
    timeoutValue: Int?,
    streamingEnabled: Boolean,
    thinkingEnabled: Boolean,
    autoCompactionEnabled: Boolean,
    queueFlushMode: String,
    userInstruction: String,
) {
    config.fontSize = fontSize.clampFontSize()
    config.contextLength = contextLength.coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH)
    config.loadLimit = loadLimitValue ?: config.loadLimit
    config.maxParallelSubAgents = maxParallelValue ?: config.maxParallelSubAgents
    config.timeoutSeconds = timeoutValue ?: config.timeoutSeconds
    config.streamingEnabled = streamingEnabled
    config.thinkingEnabled = thinkingEnabled
    config.autoCompactionEnabled = autoCompactionEnabled
    config.queueFlushMode = queueFlushMode
    config.userModelInstruction = userInstruction
    config.save()
}
