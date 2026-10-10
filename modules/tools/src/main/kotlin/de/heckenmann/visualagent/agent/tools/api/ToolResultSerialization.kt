package de.heckenmann.visualagent.agent.tools.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val providerResultJson =
    Json {
        encodeDefaults = true
        explicitNulls = true
    }

/** Encodes the canonical single-call contract, including explicit null fields. */
fun ToolResultEnvelope.toProviderJson(): String = providerResultJson.encodeToString(this)

/** Projects the same canonical contract into a batch without changing data or error types. */
fun ToolResultEnvelope.toProviderElement(): JsonObject =
    providerResultJson.encodeToJsonElement(ToolResultEnvelope.serializer(), this).jsonObject
