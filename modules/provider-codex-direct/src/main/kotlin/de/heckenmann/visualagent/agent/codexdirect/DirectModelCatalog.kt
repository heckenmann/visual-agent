package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parses the selected billing route's catalog, retaining positive numeric context-window metadata when supplied. */
internal fun parseDirectModels(
    body: String,
    mode: DirectProviderAuthMode,
): List<ProviderModelConfig> {
    val catalog = Json.parseToJsonElement(body).jsonObject
    return when (mode) {
        DirectProviderAuthMode.API_KEY ->
            catalog.getValue("data").jsonArray.map { entry ->
                val id =
                    entry.jsonObject
                        .getValue("id")
                        .jsonPrimitive.content
                require(id.isNotBlank()) { "The model catalog contains a blank identifier" }
                ProviderModelConfig(id)
            }
        DirectProviderAuthMode.CHATGPT_OAUTH ->
            catalog.getValue("models").jsonArray.mapNotNull { entry ->
                val model = entry.jsonObject
                if (model["visibility"]?.jsonPrimitive?.content != "list") return@mapNotNull null
                val id = model.getValue("slug").jsonPrimitive.content
                require(id.isNotBlank()) { "The model catalog contains a blank identifier" }
                val contextWindow = model["context_window"] as? JsonPrimitive
                ProviderModelConfig(
                    id,
                    name = model["display_name"]?.jsonPrimitive?.content?.ifBlank { id } ?: id,
                    contextLimit = contextWindow?.takeUnless { it.isString }?.intOrNull?.takeIf { it > 0 },
                )
            }
    }.distinctBy { it.id }
}
