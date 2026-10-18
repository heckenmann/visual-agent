package de.heckenmann.visualagent.agent.config

import de.heckenmann.visualagent.agent.SubAgent

/** Preserves stored template selection and the existing explicit-tool fallback. */
internal fun AgentToolConfigService.resolveTemplateName(agent: SubAgent): String {
    val template = agent.config.templateName?.ifBlank { null }
    if (template != null) return template
    val tools = agent.config.tools
    return defaultConfigs()
        .firstOrNull { config ->
            tools != null && tools.toSet() == config.tools.toSet()
        }?.id ?: "researcher"
}
