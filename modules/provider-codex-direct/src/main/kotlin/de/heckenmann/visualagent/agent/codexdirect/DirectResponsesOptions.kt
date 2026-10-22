package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ModelParameters
import de.heckenmann.visualagent.agent.nativeResponseSchema
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import org.springframework.ai.tool.ToolCallback

/** Builds route-specific options without placing credentials in the prompt. */
internal fun directResponsesOptions(
    request: ChatRequestContext,
    mode: DirectProviderAuthMode,
    callbacks: List<ToolCallback>,
): OpenAiResponsesChatOptions {
    require(request.options.keys.all { it in DIRECT_OPTIONS }) { "The request contains unsupported direct-provider options" }
    val model = requireNotNull(request.model).also { require(it.isNotBlank()) { "A model must be selected" } }
    val options = OpenAiResponsesChatOptions.builder().model(model).toolCallbacks(callbacks)
    request.nativeResponseSchema()?.let { options.outputSchema(it.json) }
    request.options.directOption("reasoningEffort", "reasoning_effort")?.let(options::reasoningEffort)
    request.options.directOption("reasoningSummary", "reasoning_summary")?.let(options::reasoningSummary)
    request.options["verbosity"]?.let(options::verbosity)
    request.options.directOption("serviceTier", "service_tier")?.let(options::serviceTier)
    request.options.directOption("parallelToolCalls", "parallel_tool_calls")?.let { options.parallelToolCalls(it.toBooleanStrict()) }
    request.options["strict"]?.let { options.strict(it.toBooleanStrict()) }
    request.options.directOption("toolChoice", "tool_choice")?.let {
        require(it in setOf("auto", "none", "required")) { "Direct toolChoice must be auto, none, or required" }
        require(it != "required" || callbacks.isNotEmpty()) { "Required toolChoice needs enabled tools" }
        options.toolChoice(it)
    }
    val parameters =
        ModelParameters(
            temperature = request.parameters.temperature ?: request.options["temperature"]?.toDouble(),
            topP = request.parameters.topP ?: request.options.directOption("topP", "top_p")?.toDouble(),
            maxTokens = request.parameters.maxTokens ?: request.options.directOption("maxTokens", "max_tokens")?.toInt(),
        )
    if (mode == DirectProviderAuthMode.API_KEY) {
        parameters.maxTokens?.let(options::maxOutputTokens)
        parameters.temperature?.let(options::temperature)
        parameters.topP?.let(options::topP)
    } else {
        require(parameters.temperature == null && parameters.topP == null) {
            "ChatGPT plan requests do not support temperature or topP"
        }
    }
    return options.build()
}

private fun Map<String, String>.directOption(
    canonical: String,
    alias: String,
): String? {
    require(
        this[canonical] == null || this[alias] == null || this[canonical] == this[alias],
    ) { "Conflicting direct-provider option aliases" }
    return this[canonical] ?: this[alias]
}

private val DIRECT_OPTIONS =
    setOf(
        "reasoningEffort",
        "reasoning_effort",
        "reasoningSummary",
        "reasoning_summary",
        "verbosity",
        "serviceTier",
        "service_tier",
        "parallelToolCalls",
        "parallel_tool_calls",
        "toolChoice",
        "tool_choice",
        "strict",
        "temperature",
        "topP",
        "top_p",
        "maxTokens",
        "max_tokens",
    )
