package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolAgentConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Allowed model capability names for JSON-driven sub-agent configuration tools. */
internal val AGENT_MODEL_CAPABILITIES =
    setOf("CHAT", "VISION", "AUDIO_INPUT", "AUDIO_OUTPUT", "IMAGE_GENERATION", "EMBEDDING", "TOOLS")

/** Merges and checks tool-supplied capability toggles and per-feature model assignments. */
internal fun applyCapabilityToolInput(
    base: ToolAgentConfig,
    input: JsonObject,
): ToolAgentConfig {
    val enabled =
        input["enabledCapabilities"]?.let { value ->
            require(value is JsonArray) { "enabledCapabilities must be an array" }
            value
                .map { item ->
                    require(item is JsonPrimitive && item.isString) { "Capability names must be strings" }
                    item.content.also { require(it in AGENT_MODEL_CAPABILITIES) { "Unknown capability '$it'" } }
                }.toSet()
        } ?: base.enabledCapabilities
    val models =
        input["capabilityModels"]?.let { value ->
            require(value is JsonObject) { "capabilityModels must be a JSON object" }
            value.mapValues { (capability, model) ->
                require(capability in AGENT_MODEL_CAPABILITIES) { "Unknown capability '$capability'" }
                require(model is JsonPrimitive && model.isString && model.content.isNotBlank()) {
                    "Model for $capability must be a non-empty string"
                }
                model.content
            }
        } ?: base.capabilityModels
    require("CHAT" in enabled) { "CHAT capability cannot be disabled" }
    require(models.keys.all { it in enabled }) { "Assign models only to enabled capabilities" }
    return base.copy(enabledCapabilities = enabled, capabilityModels = models)
}
