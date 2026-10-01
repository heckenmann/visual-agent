package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.MODEL_SELECTION_TOOL_ID
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionPort
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionRequest
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import reactor.core.publisher.Mono

/** Lets an authorized agent change only its own model for subsequent agent requests. */
@AgentTool
class ModelSelectionTool(
    private val selection: ModelSelectionPort,
) : VisualAgentTool {
    private val responseJson = Json { encodeDefaults = true }

    override val definition =
        ToolDefinition(
            id = ToolId(MODEL_SELECTION_TOOL_ID),
            name = ToolId(MODEL_SELECTION_TOOL_ID).toFunctionName(),
            description =
                "Inspect or change your own provider/model. get returns your current selection; " +
                    "listProviders lists enabled profiles; listModels lists selectable models for providerId " +
                    "(defaults to your current provider). Lists accept offset and limit (1-50). " +
                    "refreshModels queries that provider's API, saves the refreshed catalog, and returns a model page " +
                    "without changing your selection. " +
                    "set requires modelId and optionally providerId. Main-agent changes update Conversation settings; " +
                    "sub-agent changes affect only the caller and require an explicit tool grant. " +
                    "Changes apply to the next agent request, not the current in-flight provider request.",
            inputSchema =
                """
                {
                    "type":"object",
                    "properties":{
                        "action":{"type":"string","enum":["get","listProviders","listModels","refreshModels","set"]},
                        "providerId":{"type":"string","minLength":1},
                        "modelId":{"type":"string","minLength":1},
                        "offset":{"type":"integer","minimum":0,"maximum":100000},
                        "limit":{"type":"integer","minimum":1,"maximum":50}
                    },
                    "required":["action"],"additionalProperties":false
                }
                """.trimIndent(),
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = checkNotNull(executeReactive(inputJson, context).block())

    override fun executeReactive(
        inputJson: String,
        context: Map<String, Any>,
    ): Mono<ToolResult> =
        Mono
            .defer {
                val input = Json.parseToJsonElement(inputJson).jsonObject
                val arguments = JsonObject(input.filterKeys { it != "timeoutSeconds" && it != "async" })
                val request = Json.decodeFromJsonElement<ModelSelectionRequest>(arguments)
                require(request.offset in 0..100000 && request.limit in 1..50) { "Use offset 0-100000 and limit 1-50." }
                require(request.providerId == null || request.providerId.isNotBlank()) { "providerId must not be blank." }
                selection.execute(request, context).map { snapshot ->
                    val data = responseJson.encodeToJsonElement(snapshot)
                    ToolResult(MODEL_SELECTION_TOOL_ID, true, data.toString(), data = data)
                }
            }.onErrorResume(IllegalArgumentException::class.java) {
                Mono.just(
                    failure(
                        MODEL_SELECTION_TOOL_ID,
                        "TOOL_ARGUMENTS: Invalid selection request. Use get, listProviders, listModels, or refreshModels; " +
                            "set requires a selectable modelId. Do not supply a target agent or unsupported arguments.",
                    ),
                )
            }
}
