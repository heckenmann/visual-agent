package de.heckenmann.visualagent.agent.tools.api

import kotlinx.serialization.Serializable
import reactor.core.publisher.Mono

/** Stable permission and persistence identity for self-directed model selection. */
const val MODEL_SELECTION_TOOL_ID = "model:selection"

/** Validated request for inspecting or changing the calling agent's model. */
@Serializable
data class ModelSelectionRequest(
    val action: String,
    val providerId: String? = null,
    val modelId: String? = null,
    val offset: Int = 0,
    val limit: Int = 20,
)

/** Effective provider and model of the caller, without credentials or provider options. */
@Serializable
data class AgentModelSelection(
    val agentId: String,
    val providerId: String,
    val modelId: String,
)

/** Safe discovery entry for a configured provider or selectable model. */
@Serializable
data class ModelSelectionOption(
    val id: String,
    val name: String,
)

/** Bounded discovery page or acknowledgement of a persisted model selection. */
@Serializable
data class ModelSelectionResult(
    val selection: AgentModelSelection,
    val items: List<ModelSelectionOption> = emptyList(),
    val offset: Int = 0,
    val total: Int = 0,
    val effectiveFrom: String = "next_agent_request",
)

/** Server-owned, caller-scoped model selection using trusted tool execution metadata. */
fun interface ModelSelectionPort {
    /** Authorizes the caller and executes a bounded query or persisted selection change. */
    fun execute(
        request: ModelSelectionRequest,
        context: Map<String, Any>,
    ): Mono<ModelSelectionResult>
}
