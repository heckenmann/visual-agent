package de.heckenmann.visualagent.agent.config

/**
 * Persisted tool configuration for one agent template.
 *
 * @property id Stable config ID
 * @property name Display name
 * @property description Purpose shown in the UI or logs
 * @property model Preferred model for this agent
 * @property systemPrompt Agent-specific system prompt
 * @property tools Tool IDs allowed for this agent
 * @property maxTurns Maximum autonomous loop turns
 * @property enabled Whether this config can be used
 * @see docs/usecases/uc_0000019_configure_agent_tools.md
 */
data class SubAgentToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val model: String = "nemotron-3-super:cloud",
    val systemPrompt: String = "",
    val tools: List<String>,
    val maxTurns: Int = 5,
    val enabled: Boolean = true,
)
