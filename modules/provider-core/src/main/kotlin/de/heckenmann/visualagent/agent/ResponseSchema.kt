package de.heckenmann.visualagent.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Provider-neutral JSON Schema requested for a complete model response. */
data class ResponseSchema(
    val json: String,
) {
    init {
        Json.parseToJsonElement(json).jsonObject
    }
}

/**
 * Returns a native schema only with positive protocol, capability, or configuration evidence.
 * Unknown support stays prompt-only. An explicit false disables even protocol-level support.
 */
fun ChatRequestContext.nativeResponseSchema(protocolSupportsSchema: Boolean = false): ResponseSchema? {
    val profile = providerProfile
    val configured =
        options["structuredOutput.native"]
            ?: profile
                ?.models
                ?.firstOrNull { it.id == model }
                ?.options
                ?.get("structuredOutput.native")
            ?: profile?.options?.get("structuredOutput.native")
    val supported =
        configured?.toBooleanStrictOrNull()
            ?: (protocolSupportsSchema || "structured_outputs" in modelCapabilities)
    return responseSchema.takeIf { supported }
}
